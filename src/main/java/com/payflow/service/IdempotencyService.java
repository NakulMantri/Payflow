package com.payflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.dto.PaymentResponse;
import com.payflow.entity.IdempotencyRecord;
import com.payflow.enums.IdempotencyStatus;
import com.payflow.exception.IdempotencyConflictException;
import com.payflow.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private static final String REDIS_IDEMP_PREFIX = "payflow:idemp:";
    private static final String STATUS_IN_FLIGHT = "IN_FLIGHT";

    private final RedisTemplate<String, Object> redisTemplate;
    private final IdempotencyRecordRepository recordRepository;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;
    private final long lockTimeoutSeconds;

    // Resilient local in-memory fallback cache if Redis is temporarily unreachable
    private final Map<String, String> localFallbackCache = new ConcurrentHashMap<>();

    public IdempotencyService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
            IdempotencyRecordRepository recordRepository,
            ObjectMapper objectMapper,
            @Value("${payflow.idempotency.ttl-seconds:86400}") long ttlSeconds,
            @Value("${payflow.idempotency.lock-timeout-seconds:120}") long lockTimeoutSeconds) {
        this.redisTemplate = redisTemplate;
        this.recordRepository = recordRepository;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
        this.lockTimeoutSeconds = lockTimeoutSeconds;
    }

    public static class IdempotencyCheckResult {
        private final boolean isCached;
        private final PaymentResponse cachedResponse;

        public IdempotencyCheckResult(boolean isCached, PaymentResponse cachedResponse) {
            this.isCached = isCached;
            this.cachedResponse = cachedResponse;
        }

        public boolean isCached() { return isCached; }
        public PaymentResponse getCachedResponse() { return cachedResponse; }
    }

    /**
     * Atomically checks and acquires an idempotency lock for the given key and user.
     * Returns cached response if already completed, throws 409 if in-flight, or acquires lock if new.
     */
    @Transactional
    public IdempotencyCheckResult acquireOrCheck(String idempotencyKey, Long userId, Object requestPayload) {
        String redisKey = REDIS_IDEMP_PREFIX + idempotencyKey;
        String requestHash = computeHash(requestPayload);

        // 1. Try Redis first if available
        if (redisTemplate != null) {
            try {
                Object existingRedisVal = redisTemplate.opsForValue().get(redisKey);
                if (existingRedisVal != null) {
                    String valStr = existingRedisVal.toString();
                    if (STATUS_IN_FLIGHT.equals(valStr)) {
                        log.warn("Idempotency conflict (Redis): Key {} is IN_FLIGHT", idempotencyKey);
                        throw new IdempotencyConflictException("A payment request with Idempotency-Key '" + idempotencyKey + "' is already in progress.");
                    } else {
                        // Cached completed response
                        log.info("Idempotency cache HIT (Redis): Key {}", idempotencyKey);
                        PaymentResponse cached = objectMapper.readValue(valStr, PaymentResponse.class);
                        return new IdempotencyCheckResult(true, cached);
                    }
                }

                // Attempt atomic lock acquisition in Redis
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, STATUS_IN_FLIGHT, Duration.ofSeconds(lockTimeoutSeconds));
                if (Boolean.FALSE.equals(acquired)) {
                    log.warn("Idempotency race condition (Redis): Lock acquisition failed for key {}", idempotencyKey);
                    throw new IdempotencyConflictException("A concurrent payment request with Idempotency-Key '" + idempotencyKey + "' is already processing.");
                }
            } catch (IdempotencyConflictException e) {
                throw e;
            } catch (Exception redisEx) {
                log.warn("Redis error, falling back to DB/Local: {}", redisEx.getMessage());
                return checkDbAndLocal(idempotencyKey, userId, requestHash);
            }
        } else {
            return checkDbAndLocal(idempotencyKey, userId, requestHash);
        }

        // Persist IN_FLIGHT state in DB for audit trail
        try {
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);
            IdempotencyRecord record = new IdempotencyRecord(idempotencyKey, userId, requestHash, IdempotencyStatus.IN_FLIGHT, expiresAt);
            recordRepository.save(record);
        } catch (Exception dbEx) {
            log.warn("Could not save initial DB idempotency record: {}", dbEx.getMessage());
        }

        return new IdempotencyCheckResult(false, null);
    }

    private IdempotencyCheckResult checkDbAndLocal(String idempotencyKey, Long userId, String requestHash) {
        // Fallback check in DB
        Optional<IdempotencyRecord> dbRecord = recordRepository.findById(idempotencyKey);
        if (dbRecord.isPresent()) {
            IdempotencyRecord record = dbRecord.get();
            if (record.getStatus() == IdempotencyStatus.IN_FLIGHT) {
                throw new IdempotencyConflictException("A payment request with Idempotency-Key '" + idempotencyKey + "' is already in progress.");
            } else if (record.getStatus() == IdempotencyStatus.COMPLETED && record.getResponseBody() != null) {
                try {
                    PaymentResponse cached = objectMapper.readValue(record.getResponseBody(), PaymentResponse.class);
                    return new IdempotencyCheckResult(true, cached);
                } catch (Exception parseEx) {
                    log.error("Failed to parse DB cached response: {}", parseEx.getMessage());
                }
            }
        }

        // Local fallback check
        String localVal = localFallbackCache.get(idempotencyKey);
        if (localVal != null) {
            if (STATUS_IN_FLIGHT.equals(localVal)) {
                throw new IdempotencyConflictException("A payment request with Idempotency-Key '" + idempotencyKey + "' is already in progress.");
            } else {
                try {
                    PaymentResponse cached = objectMapper.readValue(localVal, PaymentResponse.class);
                    return new IdempotencyCheckResult(true, cached);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        localFallbackCache.put(idempotencyKey, STATUS_IN_FLIGHT);

        try {
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(ttlSeconds);
            IdempotencyRecord record = new IdempotencyRecord(idempotencyKey, userId, requestHash, IdempotencyStatus.IN_FLIGHT, expiresAt);
            recordRepository.save(record);
        } catch (Exception dbEx) {
            log.warn("Could not save initial DB idempotency record: {}", dbEx.getMessage());
        }

        return new IdempotencyCheckResult(false, null);
    }

    /**
     * Marks the idempotency key as COMPLETED and caches the resulting PaymentResponse.
     */
    @Transactional
    public void recordSuccess(String idempotencyKey, Long userId, PaymentResponse response) {
        String redisKey = REDIS_IDEMP_PREFIX + idempotencyKey;
        try {
            String json = objectMapper.writeValueAsString(response);
            if (redisTemplate != null) {
                try {
                    redisTemplate.opsForValue().set(redisKey, json, Duration.ofSeconds(ttlSeconds));
                } catch (Exception e) {
                    localFallbackCache.put(idempotencyKey, json);
                }
            } else {
                localFallbackCache.put(idempotencyKey, json);
            }

            // Update in DB
            recordRepository.findById(idempotencyKey).ifPresent(record -> {
                record.setStatus(IdempotencyStatus.COMPLETED);
                record.setHttpStatus(200);
                record.setResponseBody(json);
                recordRepository.save(record);
            });
            log.info("Idempotency key marked COMPLETED: key={}, txnRef={}", idempotencyKey, response.getTransactionRef());
        } catch (Exception e) {
            log.error("Error serializing PaymentResponse for idempotency cache: {}", e.getMessage(), e);
        }
    }

    /**
     * Releases or marks key as FAILED if an error happens before transaction commitment.
     */
    @Transactional
    public void releaseLock(String idempotencyKey) {
        String redisKey = REDIS_IDEMP_PREFIX + idempotencyKey;
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception e) {
                localFallbackCache.remove(idempotencyKey);
            }
        } else {
            localFallbackCache.remove(idempotencyKey);
        }
        try {
            recordRepository.deleteById(idempotencyKey);
        } catch (Exception e) {
            log.warn("Failed to remove DB idempotency record on release: {}", e.getMessage());
        }
        log.info("Released idempotency lock for key: {}", idempotencyKey);
    }

    private String computeHash(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "hash_" + System.currentTimeMillis();
        }
    }
}

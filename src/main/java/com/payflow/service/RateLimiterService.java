package com.payflow.service;

import com.payflow.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private static final String REDIS_RATE_PREFIX = "payflow:rate:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean enabled;
    private final int defaultCapacity;
    private final int defaultRefillSeconds;

    // In-memory sliding window fallback counters
    private final Map<String, WindowCounter> localCounters = new ConcurrentHashMap<>();

    public RateLimiterService(
            @org.springframework.beans.factory.annotation.Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
            @Value("${payflow.rate-limiting.enabled:true}") boolean enabled,
            @Value("${payflow.rate-limiting.capacity:20}") int defaultCapacity,
            @Value("${payflow.rate-limiting.refill-seconds:60}") int defaultRefillSeconds) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.defaultCapacity = defaultCapacity;
        this.defaultRefillSeconds = defaultRefillSeconds;
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final int limit;
        private final int remaining;
        private final long resetSeconds;

        public RateLimitResult(boolean allowed, int limit, int remaining, long resetSeconds) {
            this.allowed = allowed;
            this.limit = limit;
            this.remaining = remaining;
            this.resetSeconds = resetSeconds;
        }

        public boolean isAllowed() { return allowed; }
        public int getLimit() { return limit; }
        public int getRemaining() { return remaining; }
        public long getResetSeconds() { return resetSeconds; }
    }

    public static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStartTime = System.currentTimeMillis();

        public synchronized int incrementAndGet(int maxWindowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStartTime > maxWindowMs) {
                count.set(0);
                windowStartTime = now;
            }
            return count.incrementAndGet();
        }

        public long getRemainingSeconds(int maxWindowMs) {
            long elapsed = System.currentTimeMillis() - windowStartTime;
            return Math.max(0, (maxWindowMs - elapsed) / 1000);
        }
    }

    /**
     * Enforces rate limiting per user key.
     * Throws RateLimitExceededException if exceeded.
     */
    public RateLimitResult checkLimit(String userKey) {
        if (!enabled) {
            return new RateLimitResult(true, defaultCapacity, defaultCapacity, 0);
        }

        String redisKey = REDIS_RATE_PREFIX + userKey;
        if (redisTemplate != null) {
            try {
                Long currentCount = redisTemplate.opsForValue().increment(redisKey);
                if (currentCount != null && currentCount == 1L) {
                    redisTemplate.expire(redisKey, Duration.ofSeconds(defaultRefillSeconds));
                }

                long current = currentCount != null ? currentCount : 1L;
                Long expire = redisTemplate.getExpire(redisKey);
                long resetSeconds = (expire != null && expire > 0) ? expire : defaultRefillSeconds;
                int remaining = (int) Math.max(0, defaultCapacity - current);

                if (current > defaultCapacity) {
                    log.warn("Rate limit exceeded for user: {} (count={}, limit={})", userKey, current, defaultCapacity);
                    throw new RateLimitExceededException("Rate limit exceeded. Maximum " + defaultCapacity + " payment requests per " + defaultRefillSeconds + " seconds. Try again in " + resetSeconds + "s.");
                }

                return new RateLimitResult(true, defaultCapacity, remaining, resetSeconds);
            } catch (RateLimitExceededException e) {
                throw e;
            } catch (Exception redisEx) {
                log.debug("Redis rate-limiter unavailable, using thread-safe local sliding window: {}", redisEx.getMessage());
            }
        }

        WindowCounter counter = localCounters.computeIfAbsent(userKey, k -> new WindowCounter());
            int count = counter.incrementAndGet(defaultRefillSeconds * 1000);
            long resetSec = counter.getRemainingSeconds(defaultRefillSeconds * 1000);
            int remaining = Math.max(0, defaultCapacity - count);

            if (count > defaultCapacity) {
                log.warn("Rate limit exceeded (local) for user: {} (count={}, limit={})", userKey, count, defaultCapacity);
                throw new RateLimitExceededException("Rate limit exceeded. Maximum " + defaultCapacity + " payment requests per " + defaultRefillSeconds + " seconds. Try again in " + resetSec + "s.");
            }

            return new RateLimitResult(true, defaultCapacity, remaining, resetSec);
    }
}

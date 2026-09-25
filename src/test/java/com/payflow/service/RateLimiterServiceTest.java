package com.payflow.service;

import com.payflow.exception.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("Should allow requests up to the configured limit and reject excess")
    void testRateLimiter_underAndOverCapacity() {
        // Test in-memory fallback by letting Redis throw connection exception
        RateLimiterService service = new RateLimiterService(redisTemplate, true, 3, 60);

        String userKey = "user_rate_test_1";

        // Request 1: allowed
        RateLimiterService.RateLimitResult res1 = service.checkLimit(userKey);
        assertThat(res1.isAllowed()).isTrue();
        assertThat(res1.getRemaining()).isEqualTo(2);

        // Request 2: allowed
        RateLimiterService.RateLimitResult res2 = service.checkLimit(userKey);
        assertThat(res2.isAllowed()).isTrue();
        assertThat(res2.getRemaining()).isEqualTo(1);

        // Request 3: allowed
        RateLimiterService.RateLimitResult res3 = service.checkLimit(userKey);
        assertThat(res3.isAllowed()).isTrue();
        assertThat(res3.getRemaining()).isEqualTo(0);

        // Request 4: rejected!
        assertThatThrownBy(() -> service.checkLimit(userKey))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded");
    }
}

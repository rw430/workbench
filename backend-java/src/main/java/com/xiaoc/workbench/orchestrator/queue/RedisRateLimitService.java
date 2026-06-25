package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.common.web.InfrastructureUnavailableException;
import com.xiaoc.workbench.common.web.RateLimitExceededException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisRateLimitService implements RateLimitService {
    private final StringRedisTemplate redisTemplate;
    private final RunStartRateLimitProperties properties;
    private final RedisFeatureProperties redisFeatureProperties;

    public RedisRateLimitService(
            StringRedisTemplate redisTemplate,
            RunStartRateLimitProperties properties,
            RedisFeatureProperties redisFeatureProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.redisFeatureProperties = redisFeatureProperties;
    }

    @Override
    public void checkAllowed(String actorId, String action) {
        long window = Instant.now().getEpochSecond() / properties.windowSeconds();
        String key = "xiaoc:rate-limit:" + actorId + ":" + action + ":" + window;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(properties.windowSeconds()));
            }
            if (count != null && count > properties.maxRequests()) {
                throw new RateLimitExceededException(action + " rate limit exceeded for " + actorId);
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            if (redisFeatureProperties.failOpen()) {
                return;
            }
            throw new InfrastructureUnavailableException("Redis rate limit unavailable: " + exception.getMessage());
        }
    }
}
package com.xiaoc.workbench.orchestrator.queue;

import com.xiaoc.workbench.common.web.InfrastructureUnavailableException;
import com.xiaoc.workbench.common.web.RunAlreadyLockedException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "xiaoc.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisRunConcurrencyGuard implements RunConcurrencyGuard {
    private static final String KEY_PREFIX = "xiaoc:run-lock:";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RunLockProperties properties;
    private final RedisFeatureProperties redisFeatureProperties;

    public RedisRunConcurrencyGuard(
            StringRedisTemplate redisTemplate,
            RunLockProperties properties,
            RedisFeatureProperties redisFeatureProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.redisFeatureProperties = redisFeatureProperties;
    }

    @Override
    public <T> T runWithLock(String runId, Supplier<T> callback) {
        String key = KEY_PREFIX + runId;
        String ownerToken = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, ownerToken, Duration.ofSeconds(properties.ttlSeconds()));
            if (!Boolean.TRUE.equals(acquired)) {
                throw new RunAlreadyLockedException("Run is already being advanced: " + runId);
            }
            return callback.get();
        } catch (RunAlreadyLockedException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            if (redisFeatureProperties.failOpen()) {
                return callback.get();
            }
            throw new InfrastructureUnavailableException("Redis run lock unavailable: " + exception.getMessage());
        } finally {
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), ownerToken);
            } catch (DataAccessException ignored) {
            }
        }
    }
}
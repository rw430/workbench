package com.xiaoc.workbench.orchestrator.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "spring.main.lazy-initialization=true",
        "xiaoc.queue.mode=rabbit",
        "xiaoc.redis.enabled=false",
        "xiaoc.rabbitmq.enabled=false",
        "xiaoc.run-lock.ttl-seconds=45",
        "xiaoc.rate-limit.run-start.max-requests=7",
        "xiaoc.rate-limit.run-start.window-seconds=30",
        "xiaoc.rabbitmq.run-start-exchange=xiaoc.run.test",
        "xiaoc.rabbitmq.run-start-queue=xiaoc.run.start.test",
        "xiaoc.rabbitmq.run-start-routing-key=run.start.test"
})
class QueuePropertiesTest {
    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private RedisFeatureProperties redisFeatureProperties;

    @Autowired
    private RunLockProperties runLockProperties;

    @Autowired
    private RunStartRateLimitProperties rateLimitProperties;

    @Autowired
    private RabbitRunQueueProperties rabbitProperties;

    @Test
    void bindsQueueRedisAndRabbitProperties() {
        assertThat(queueProperties.mode()).isEqualTo(QueueMode.RABBIT);
        assertThat(redisFeatureProperties.enabled()).isFalse();
        assertThat(redisFeatureProperties.failOpen()).isFalse();
        assertThat(runLockProperties.ttlSeconds()).isEqualTo(45);
        assertThat(rateLimitProperties.maxRequests()).isEqualTo(7);
        assertThat(rateLimitProperties.windowSeconds()).isEqualTo(30);
        assertThat(rabbitProperties.runStartExchange()).isEqualTo("xiaoc.run.test");
        assertThat(rabbitProperties.runStartQueue()).isEqualTo("xiaoc.run.start.test");
        assertThat(rabbitProperties.runStartRoutingKey()).isEqualTo("run.start.test");
    }
}

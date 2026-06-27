package com.xiaoc.workbench.orchestrator.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "xiaoc.queue.mode", havingValue = "rabbit")
public class RabbitRunQueueConfig {
    @Bean
    TopicExchange runStartExchange(RabbitRunQueueProperties properties) {
        return new TopicExchange(properties.runStartExchange(), true, false);
    }

    @Bean
    Queue runStartQueue(RabbitRunQueueProperties properties) {
        return new Queue(properties.runStartQueue(), true);
    }

    @Bean
    Binding runStartBinding(TopicExchange runStartExchange, Queue runStartQueue, RabbitRunQueueProperties properties) {
        return BindingBuilder.bind(runStartQueue)
                .to(runStartExchange)
                .with(properties.runStartRoutingKey());
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
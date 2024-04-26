package com.hmdp.config;

import com.hmdp.utils.SystemConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitmqConfig {

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SystemConstants.SECKILL_MQ_QUEUE).build();
    }

    @Bean
    public Exchange seckillExchange() {
        return ExchangeBuilder.directExchange(SystemConstants.SECKILL_MQ_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding bindingSeckill(Queue seckillQueue, Exchange seckillExchange) {
        return BindingBuilder.bind(seckillQueue).to(seckillExchange).with(SystemConstants.SECKILL_MQ_ROUTINGKEY).noargs();

    }
}

package com.shivraj.checkout_demo.config;

import com.shivraj.checkout_demo.entity.Product;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Product> productRedisTemplate(RedisConnectionFactory connectionFactory){
        RedisTemplate<String,Product> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());

        // Typed serializer: always reads/writes as Product.class, no reliance
        // on embedded "@class" metadata in the JSON.
        JacksonJsonRedisSerializer<Product> serializer = new JacksonJsonRedisSerializer<>(Product.class);
        template.setValueSerializer(serializer);
        return template;
    }

}

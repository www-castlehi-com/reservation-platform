package com.stay.reservation.bookingpayment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stay.reservation.bookingpayment.checkout.dto.CachedProductInfo;

@Configuration
public class RedisConfig {

	@Bean
	public RedisTemplate<String, CachedProductInfo> cachedProductInfoRedisTemplate(
		RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, CachedProductInfo> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);

		StringRedisSerializer keySerializer = new StringRedisSerializer();
		template.setKeySerializer(keySerializer);
		template.setHashKeySerializer(keySerializer);

		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());

		Jackson2JsonRedisSerializer<CachedProductInfo> valueSerializer = new Jackson2JsonRedisSerializer<>(objectMapper,
			CachedProductInfo.class);
		template.setValueSerializer(valueSerializer);
		template.setHashValueSerializer(valueSerializer);

		template.afterPropertiesSet();

		return template;
	}
}

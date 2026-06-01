package com.stay.reservation.bookingpayment.booking.service;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisStockManager {

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<Long> reserveStockScript;

	public boolean reserveStock(Long productId) {
		String stockKey = "stock:product:" + productId;
		Long result = redisTemplate.execute(reserveStockScript, List.of(stockKey));
		return result != null && result == 1;
	}

	public void rollbackStock(Long productId) {
		String stockKey = "stock:product:" + productId;
		redisTemplate.opsForValue().increment(stockKey);
	}
}

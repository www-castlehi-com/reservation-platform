package com.stay.reservation.bookingpayment.booking.service;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisStockManager {

	static final String STOCK_KEY_PREFIX = "stock:product:";
	private static final long RESERVE_SUCCESS = 1L;

	private final StringRedisTemplate redisTemplate;
	private final RedisScript<Long> reserveStockScript;

	public boolean reserveStock(Long productId) {
		String stockKey = buildStockKey(productId);
		Long result = redisTemplate.execute(reserveStockScript, List.of(stockKey));
		return RESERVE_SUCCESS == result;
	}

	public void rollbackStock(Long productId) {
		redisTemplate.opsForValue().increment(buildStockKey(productId));
	}

	public int getStock(Long productId) {
		String value = redisTemplate.opsForValue().get(buildStockKey(productId));
		return value == null ? 0 : Integer.parseInt(value);
	}

	private String buildStockKey(Long productId) {
		return STOCK_KEY_PREFIX + productId;
	}
}

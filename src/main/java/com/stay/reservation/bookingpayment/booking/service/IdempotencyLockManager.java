package com.stay.reservation.bookingpayment.booking.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.stay.reservation.bookingpayment.common.exception.IdempotencyConflictException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyLockManager {

	private static final String LOCK_KEY_PREFIX = "idempotency:lock:";
	private static final Duration LOCK_TTL = Duration.ofSeconds(10);

	private final StringRedisTemplate redisTemplate;

	public String acquire(String idempotencyKey) {
		String lockKey = LOCK_KEY_PREFIX + idempotencyKey;
		Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "PROCESSING", LOCK_TTL);
		if (!Boolean.TRUE.equals(locked)) {
			throw new IdempotencyConflictException(idempotencyKey);
		}
		return lockKey;
	}

	public void release(String lockKey) {
		try {
			redisTemplate.delete(lockKey);
		} catch (Exception e) {
			log.error("Failed to release lock key={}", lockKey, e);
		}
	}
}

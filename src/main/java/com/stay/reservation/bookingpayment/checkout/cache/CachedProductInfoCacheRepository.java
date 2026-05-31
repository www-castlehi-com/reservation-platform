package com.stay.reservation.bookingpayment.checkout.cache;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import com.stay.reservation.bookingpayment.checkout.dto.CachedProductInfo;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CachedProductInfoCacheRepository {

	private static final String KEY_PREFIX = "checkout:product:";
	private static final Duration TTL = Duration.ofSeconds(5);

	private final RedisTemplate<String, CachedProductInfo> redisTemplate;

	public CachedProductInfo find(Long productId) {
		return redisTemplate.opsForValue().get(KEY_PREFIX + productId);
	}

	public void save(Long productId, CachedProductInfo info) {
		redisTemplate.opsForValue().set(KEY_PREFIX + productId, info, TTL);
	}
}

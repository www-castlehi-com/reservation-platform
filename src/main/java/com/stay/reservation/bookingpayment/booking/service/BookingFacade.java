package com.stay.reservation.bookingpayment.booking.service;

import java.time.Duration;

import com.stay.reservation.bookingpayment.common.exception.DuplicateBookingException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;
import com.stay.reservation.bookingpayment.common.exception.IdempotencyConflictException;
import com.stay.reservation.bookingpayment.common.exception.SoldOutException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingFacade {

	private final BookingService bookingService;
	private final RedisStockManager redisStockManager;
	private final StringRedisTemplate redisTemplate;

	public BookingResponse createBooking(Long userId, String idempotencyKey, BookingRequest request) {
		log.info("Initiating booking flow via Facade for user: {}, product: {}", userId, request.productId());

		String lockKey = "idempotency:lock:" + idempotencyKey;
		boolean lockAcquired = false;
		boolean stockDeducted = false;

		try {
			BookingResponse existingBooking = bookingService.checkIdempotency(idempotencyKey);
			if (existingBooking != null) {
				return existingBooking;
			}

			Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "PROCESSING", Duration.ofSeconds(10));
			if (locked == null || !locked) {
				throw new IdempotencyConflictException(idempotencyKey);
			}
			lockAcquired = true;

			if (!redisStockManager.reserveStock(request.productId())) {
				throw new SoldOutException(request.productId());
			}
			stockDeducted = true;

			return bookingService.proceedBookingTransaction(userId, idempotencyKey, request);

		} catch (Exception e) {
			if (stockDeducted) {
				redisStockManager.rollbackStock(request.productId());
			}
			if (e instanceof DataIntegrityViolationException) {
				throw new DuplicateBookingException(idempotencyKey);
			}
			throw e;
		} finally {
			if (lockAcquired) {
				redisTemplate.delete(lockKey);
			}
		}
	}
}

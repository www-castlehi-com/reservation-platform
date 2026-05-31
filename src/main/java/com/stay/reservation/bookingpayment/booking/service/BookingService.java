package com.stay.reservation.bookingpayment.booking.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.booking.domain.Booking;
import com.stay.reservation.bookingpayment.booking.domain.BookingStatus;
import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;
import com.stay.reservation.bookingpayment.booking.repository.BookingRepository;
import com.stay.reservation.bookingpayment.common.exception.DuplicateBookingException;
import com.stay.reservation.bookingpayment.common.exception.IdempotencyConflictException;
import com.stay.reservation.bookingpayment.common.exception.PriceMismatchException;
import com.stay.reservation.bookingpayment.common.exception.ProductNotFoundException;
import com.stay.reservation.bookingpayment.common.exception.SoldOutException;
import com.stay.reservation.bookingpayment.common.exception.UserNotFoundException;
import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.product.repository.ProductRepository;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

	private static final String RESERVE_LUA =
		"local stock = redis.call('GET', KEYS[1])\n" + "if not stock or tonumber(stock) <= 0 then\n" + "    return -1\n"
			+ "end\n" + "redis.call('DECR', KEYS[1])\n" + "return 1";

	private static final RedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>(RESERVE_LUA, Long.class);

	private final BookingRepository bookingRepository;
	private final ProductRepository productRepository;
	private final UserWalletRepository userWalletRepository;
	private final StringRedisTemplate redisTemplate;

	@Transactional
	public BookingResponse createBooking(Long userId, String idempotencyKey, BookingRequest request) {
		log.info("Creating booking for user: {}, product: {}, idempotencyKey: {}", userId, request.productId(),
			idempotencyKey);

		String lockKey = "idempotency:lock:" + idempotencyKey;
		String stockKey = "stock:product:" + request.productId();
		boolean lockAcquired = false;
		boolean stockDeducted = false;

		try {
			Optional<Booking> existingBooking = bookingRepository.findByIdempotencyKey(idempotencyKey);
			if (existingBooking.isPresent()) {
				log.info("1st-Tier Idempotency Hit. Returning existing booking: {}",
					existingBooking.get().getBookingNumber());
				return BookingResponse.from(existingBooking.get());
			}

			Boolean locked = redisTemplate.opsForValue()
				.setIfAbsent(lockKey, "PROCESSING", java.time.Duration.ofSeconds(10));
			if (locked == null || !locked) {
				log.warn("2nd-Tier Idempotency Lock Collision detected for key: {}", lockKey);
				throw new IdempotencyConflictException(idempotencyKey);
			}
			lockAcquired = true;

			if (!userWalletRepository.existsById(userId)) {
				throw new UserNotFoundException(userId);
			}

			Product product = productRepository.findById(request.productId())
				.orElseThrow(() -> new ProductNotFoundException(request.productId()));

			if (!product.getPrice().equals(request.totalAmount())) {
				log.warn("Price mismatch for product: {}. Expected: {}, Request: {}", request.productId(),
					product.getPrice(), request.totalAmount());
				throw new PriceMismatchException();
			}

			Long reserveResult = redisTemplate.execute(RESERVE_SCRIPT, List.of(stockKey));
			if (reserveResult == null || reserveResult == -1) {
				log.warn("3-Tier Stock check: Product is sold out in Redis. ProductId: {}", request.productId());
				throw new SoldOutException(request.productId());
			}
			stockDeducted = true;

			String bookingNumber = generateBookingNumber();
			Booking booking = Booking.builder()
				.bookingNumber(bookingNumber)
				.idempotencyKey(idempotencyKey)
				.userId(userId)
				.productId(request.productId())
				.totalAmount(request.totalAmount())
				.status(BookingStatus.CONFIRMED)
				.customerName(request.customerName())
				.customerPhone(request.customerPhone())
				.build();

			Booking savedBooking = bookingRepository.saveAndFlush(booking);
			log.info("Booking created successfully in DB [Phase 3]. ID: {}, Number: {}", savedBooking.getId(),
				savedBooking.getBookingNumber());
			return BookingResponse.from(savedBooking);

		} catch (Exception e) {
			log.error(
				"Exception occurred during booking creation flow. Triggering Saga compensation if needed. Exception: {}",
				e.getMessage());

			if (stockDeducted) {
				log.info("Saga Compensation: Incrementing Redis stock back for product: {}", request.productId());
				redisTemplate.opsForValue().increment(stockKey);
			}

			if (e instanceof DataIntegrityViolationException) {
				throw new DuplicateBookingException(idempotencyKey);
			}

			throw e;
		} finally {
			if (lockAcquired) {
				log.info("Releasing In-Flight lock for key: {}", lockKey);
				redisTemplate.delete(lockKey);
			}
		}
	}

	private String generateBookingNumber() {
		String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		int randomNum = ThreadLocalRandom.current().nextInt(10000, 100000);
		return "B" + dateStr + "-" + randomNum;
	}
}
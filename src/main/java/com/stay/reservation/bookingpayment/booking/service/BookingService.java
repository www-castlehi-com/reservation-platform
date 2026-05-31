package com.stay.reservation.bookingpayment.booking.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.booking.domain.Booking;
import com.stay.reservation.bookingpayment.booking.domain.BookingStatus;
import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;
import com.stay.reservation.bookingpayment.booking.repository.BookingRepository;
import com.stay.reservation.bookingpayment.common.exception.DuplicateBookingException;
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

	private final BookingRepository bookingRepository;
	private final ProductRepository productRepository;
	private final UserWalletRepository userWalletRepository;

	@Transactional
	public BookingResponse createBooking(Long userId, String idempotencyKey, BookingRequest request) {
		log.info("Creating booking for user: {}, product: {}, idempotencyKey: {}", userId, request.productId(),
			idempotencyKey);

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

		if (product.getTotalStock() <= 0) {
			log.warn("Product is sold out: {}", request.productId());
			throw new SoldOutException(request.productId());
		}
		product.decreaseStock();

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

		try {
			Booking savedBooking = bookingRepository.saveAndFlush(booking);
			log.info("Booking created successfully. ID: {}, Number: {}", savedBooking.getId(),
				savedBooking.getBookingNumber());
			return BookingResponse.from(savedBooking);
		} catch (DataIntegrityViolationException e) {
			log.warn("Duplicate booking request detected for idempotencyKey: {}", idempotencyKey);
			throw new DuplicateBookingException(idempotencyKey);
		}
	}

	private String generateBookingNumber() {
		String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		int randomNum = ThreadLocalRandom.current().nextInt(10000, 100000);
		return "B" + dateStr + "-" + randomNum;
	}
}

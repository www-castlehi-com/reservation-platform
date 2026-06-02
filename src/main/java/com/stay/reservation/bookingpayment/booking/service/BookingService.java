package com.stay.reservation.bookingpayment.booking.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.stay.reservation.bookingpayment.booking.domain.Booking;
import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;
import com.stay.reservation.bookingpayment.booking.repository.BookingRepository;
import com.stay.reservation.bookingpayment.common.exception.DuplicateBookingException;
import com.stay.reservation.bookingpayment.common.exception.IdempotencyConflictException;
import com.stay.reservation.bookingpayment.common.exception.PriceMismatchException;
import com.stay.reservation.bookingpayment.common.exception.ProductNotFoundException;
import com.stay.reservation.bookingpayment.common.exception.SoldOutException;
import com.stay.reservation.bookingpayment.common.exception.UserNotFoundException;
import com.stay.reservation.bookingpayment.payment.exception.PaymentFailedException;
import com.stay.reservation.bookingpayment.payment.model.CompositePaymentResult;
import com.stay.reservation.bookingpayment.payment.model.PaymentCommand;
import com.stay.reservation.bookingpayment.payment.service.PaymentProcessor;
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
	private final IdempotencyLockManager idempotencyLockManager;
	private final RedisStockManager redisStockManager;
	private final PaymentProcessor paymentProcessor;
	private final PaymentCommandMapper paymentCommandMapper;
	private final BookingPersistenceService bookingPersistenceService;

	public BookingResponse createBooking(BookingRequest request, Long userId, String idempotencyKey) {
		Optional<BookingResponse> existing = findExistingBooking(idempotencyKey);
		if (existing.isPresent()) {
			log.info("Idempotent hit. Returning existing booking: {}", existing.get().bookingId());
			return existing.get();
		}

		String lockKey = idempotencyLockManager.acquire(idempotencyKey);

		boolean stockDeducted = false;
		CompositePaymentResult paymentResult = null;

		try {
			reserveStock(request.productId());
			stockDeducted = true;

			if (!userWalletRepository.existsById(userId)) {
				throw new UserNotFoundException(userId);
			}

			Product product = getProductAndValidatePrice(request.productId(), request.payment().totalAmount());

			paymentResult = processPayment(request.payment(), userId, idempotencyKey);

			Booking savedBooking = bookingPersistenceService.persistBookingAndPayments(request, userId, idempotencyKey,
				product, paymentResult);

			return BookingResponse.from(savedBooking);

		} catch (DuplicateBookingException | IdempotencyConflictException e) {
			throw e;
		} catch (UserNotFoundException | ProductNotFoundException | PriceMismatchException | SoldOutException e) {
			log.warn("Booking validation failed. idempotencyKey={}", idempotencyKey, e);
			compensate(stockDeducted, paymentResult, request.productId(), idempotencyKey);
			throw e;
		} catch (PaymentFailedException e) {
			log.error("Booking failed due to payment failure. idempotencyKey={}", idempotencyKey, e);
			compensate(stockDeducted, e.getResult(), request.productId(), idempotencyKey);
			throw e;
		} catch (Exception e) {
			log.error("Booking failed. idempotencyKey={}", idempotencyKey, e);
			compensate(stockDeducted, paymentResult, request.productId(), idempotencyKey);
			throw e;
		} finally {
			idempotencyLockManager.release(lockKey);
		}
	}

	private Optional<BookingResponse> findExistingBooking(String idempotencyKey) {
		return bookingRepository.findByIdempotencyKey(idempotencyKey).map(BookingResponse::from);
	}

	private Product getProductAndValidatePrice(Long productId, Long requestAmount) {
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new ProductNotFoundException(productId));
		validatePrice(product, requestAmount);
		return product;
	}

	private void reserveStock(Long productId) {
		if (!redisStockManager.reserveStock(productId)) {
			throw new SoldOutException(productId);
		}
	}

	private CompositePaymentResult processPayment(BookingRequest.Payment payment, Long userId, String idempotencyKey) {
		List<PaymentCommand> commands = paymentCommandMapper.toCommands(payment, userId, idempotencyKey);
		CompositePaymentResult paymentResult = paymentProcessor.process(commands, payment.totalAmount());
		if (!paymentResult.isAllSuccess()) {
			throw new PaymentFailedException(paymentResult);
		}
		return paymentResult;
	}

	private void compensate(boolean stockDeducted, CompositePaymentResult paymentResult, Long productId,
		String idempotencyKey) {
		if (stockDeducted) {
			try {
				redisStockManager.rollbackStock(productId);
				log.info("Stock released for product {} (idempotencyKey={})", productId, idempotencyKey);
			} catch (Exception ex) {
				log.error("Stock release failed for product {} (idempotencyKey={})", productId, idempotencyKey, ex);
			}
		}

		if (paymentResult != null && !paymentResult.compensationCompleted()) {
			log.error("ROLLBACK_FAILED: compensation incomplete. results={}", paymentResult.paymentResults());
		}
	}

	private void validatePrice(Product product, Long requestTotalAmount) {
		if (!product.getPrice().equals(requestTotalAmount)) {
			throw new PriceMismatchException();
		}
	}
}
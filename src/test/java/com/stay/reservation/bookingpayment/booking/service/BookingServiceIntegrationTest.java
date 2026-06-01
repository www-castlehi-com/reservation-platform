package com.stay.reservation.bookingpayment.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.booking.domain.BookingStatus;
import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;
import com.stay.reservation.bookingpayment.booking.repository.BookingRepository;
import com.stay.reservation.bookingpayment.payment.domain.Payment;
import com.stay.reservation.bookingpayment.payment.domain.PaymentStatus;
import com.stay.reservation.bookingpayment.payment.exception.PaymentFailedException;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;
import com.stay.reservation.bookingpayment.payment.repository.PaymentRepository;
import com.stay.reservation.bookingpayment.user.domain.UserWallet;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class BookingServiceIntegrationTest {

	@Autowired
	private BookingService bookingService;

	@Autowired
	private BookingRepository bookingRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private UserWalletRepository walletRepository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	private static final Long USER_ID = 1001L;
	private static final Long PRODUCT_ID = 1L;

	@BeforeEach
	void setUp() {
		redisTemplate.opsForValue().set("stock:product:" + PRODUCT_ID, "10");
	}

	@Test
	@DisplayName("복합 결제(카드 + 포인트) 성공 시 예약 생성 및 포인트 차감 검증")
	void compoundPaymentSuccessTest() {
		// given
		UserWallet wallet = walletRepository.findById(USER_ID).orElseThrow();
		// Adjust point balance using addPoint or deductPoint since setter is protected/private
		long currentBalance = wallet.getPointBalance();
		if (currentBalance < 100000L) {
			wallet.addPoint(100000L - currentBalance);
		} else if (currentBalance > 100000L) {
			wallet.deductPoint(currentBalance - 100000L);
		}
		walletRepository.saveAndFlush(wallet);

		String idempotencyKey = UUID.randomUUID().toString();
		BookingRequest.Payment paymentDto = new BookingRequest.Payment(
			159000L,
			List.of(
				new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 100000L, "card-token-123", null),
				new BookingRequest.Payment.Method(PaymentType.Y_POINT, 59000L, null, null)
			)
		);
		BookingRequest request = new BookingRequest(PRODUCT_ID, paymentDto, "홍길동", "010-1234-5678");

		// when
		BookingResponse response = bookingService.createBooking(request, USER_ID, idempotencyKey);

		// then
		assertThat(response).isNotNull();
		assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);

		// 포인트 차감 확인 (100000 -> 41000)
		UserWallet updatedWallet = walletRepository.findById(USER_ID).orElseThrow();
		assertThat(updatedWallet.getPointBalance()).isEqualTo(41000L);

		// 결제 기록 적재 확인
		List<Payment> payments = paymentRepository.findByBookingId(response.bookingId());
		assertThat(payments).hasSize(2);
		assertThat(payments).allMatch(p -> p.getStatus() == PaymentStatus.SUCCESS);
	}

	@Test
	@DisplayName("포인트 잔액 부족 시 결제 실패 및 Saga 보상 트랜잭션(재고 복구, 포인트 미차감, 예약 미생성) 검증")
	void compoundPaymentFailureRollbackTest() {
		// given
		UserWallet wallet = walletRepository.findById(USER_ID).orElseThrow();
		// Adjust point balance using addPoint or deductPoint since setter is protected/private
		long currentBalance = wallet.getPointBalance();
		if (currentBalance < 30000L) {
			wallet.addPoint(30000L - currentBalance);
		} else if (currentBalance > 30000L) {
			wallet.deductPoint(currentBalance - 30000L);
		}
		walletRepository.saveAndFlush(wallet);

		String idempotencyKey = UUID.randomUUID().toString();
		BookingRequest.Payment paymentDto = new BookingRequest.Payment(
			159000L,
			List.of(
				new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 100000L, "card-token-123", null),
				new BookingRequest.Payment.Method(PaymentType.Y_POINT, 59000L, null, null)
			)
		);
		BookingRequest request = new BookingRequest(PRODUCT_ID, paymentDto, "홍길동", "010-1234-5678");

		// when & then: 결제 실패 예외가 던져져야 함
		assertThrows(PaymentFailedException.class, () -> {
			bookingService.createBooking(request, USER_ID, idempotencyKey);
		});

		// 예약 미생성 확인
		assertThat(bookingRepository.findByIdempotencyKey(idempotencyKey)).isEmpty();

		// 포인트 차감되지 않음 확인
		UserWallet updatedWallet = walletRepository.findById(USER_ID).orElseThrow();
		assertThat(updatedWallet.getPointBalance()).isEqualTo(30000L);

		// 재고 복구 확인 (처음 reserveStock에서 10 -> 9 차감했다가 rollbackStock으로 다시 10이어야 함)
		String stock = redisTemplate.opsForValue().get("stock:product:" + PRODUCT_ID);
		assertThat(stock).isEqualTo("10");
	}
}

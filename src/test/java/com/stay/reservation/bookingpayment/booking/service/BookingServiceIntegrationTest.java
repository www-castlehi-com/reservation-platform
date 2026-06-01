package com.stay.reservation.bookingpayment.booking.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

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
import com.stay.reservation.bookingpayment.payment.port.PointBalancePort;
import com.stay.reservation.bookingpayment.payment.port.pg.PgClient;
import com.stay.reservation.bookingpayment.payment.port.pg.PgResponse;
import com.stay.reservation.bookingpayment.payment.port.pg.PgAuthorizeRequest;
import com.stay.reservation.bookingpayment.payment.repository.PaymentRepository;
import com.stay.reservation.bookingpayment.user.domain.UserWallet;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

import org.mockito.InOrder;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.beans.factory.annotation.Qualifier;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Transactional
class BookingServiceIntegrationTest {

	private static final Long USER_ID = 1001L;
	private static final Long PRODUCT_ID = 1L;

	@SpyBean
	@Qualifier("cardPgClient")
	private PgClient cardPgClient;

	@SpyBean
	private PointBalancePort pointBalancePort;
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
		BookingRequest.Payment paymentDto = new BookingRequest.Payment(159000L,
			List.of(new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 100000L, "card-token-123", null),
				new BookingRequest.Payment.Method(PaymentType.Y_POINT, 59000L, null, null)));
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
		BookingRequest.Payment paymentDto = new BookingRequest.Payment(159000L,
			List.of(new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 100000L, "card-token-123", null),
				new BookingRequest.Payment.Method(PaymentType.Y_POINT, 59000L, null, null)));
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

	@Test
	@DisplayName("역순 보상(Saga LIFO) - 포인트(1만)+신용카드(14.9만) 결제 도중 카드 한도초과 에러 발생 시, 포인트가 LIFO 순서에 맞춰 완벽히 롤백 환불되는지 검증")
	void lifoCompensationSuccessTest() {
		// given
		UserWallet wallet = walletRepository.findById(USER_ID).orElseThrow();
		long currentBalance = wallet.getPointBalance();
		if (currentBalance < 50000L) {
			wallet.addPoint(50000L - currentBalance);
		}
		walletRepository.saveAndFlush(wallet);

		// 카드 결제 요청(149,000원) 시 한도 초과(LIMIT_EXCEEDED) 오류가 발생하도록 스파이 스터빙
		doReturn(PgResponse.declined("LIMIT_EXCEEDED", "결제 한도 초과"))
			.when(cardPgClient).authorize(argThat(req -> req.amount() == 149000L));

		String idempotencyKey = UUID.randomUUID().toString();
		BookingRequest.Payment paymentDto = new BookingRequest.Payment(159000L,
			List.of(
				new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 149000L, "card-token-123", null),
				new BookingRequest.Payment.Method(PaymentType.Y_POINT, 10000L, null, null)
			));
		BookingRequest request = new BookingRequest(PRODUCT_ID, paymentDto, "홍길동", "010-1234-5678");

		// when & then: 결제 실패 예외가 정확하게 던져지는지 확인
		assertThrows(PaymentFailedException.class, () -> {
			bookingService.createBooking(request, USER_ID, idempotencyKey);
		});

		// 1. 예약 및 결제 내역이 DB에 생성되지 않았음을 확인
		assertThat(bookingRepository.findByIdempotencyKey(idempotencyKey)).isEmpty();

		// 2. LIFO 보상 트랜잭션이 작동하여 pointBalancePort.restore(txId, 10000)가 정상 실행되었고 유저 포인트 잔액이 50000으로 원복되었는지 검증
		UserWallet updatedWallet = walletRepository.findById(USER_ID).orElseThrow();
		assertThat(updatedWallet.getPointBalance()).isEqualTo(50000L);

		// 3. pointBalancePort.restore가 실제로 호출되었는지 검증
		verify(pointBalancePort).restore(anyString(), eq(10000L));

		// 4. Redis 재고 원복 확인
		String stock = redisTemplate.opsForValue().get("stock:product:" + PRODUCT_ID);
		assertThat(stock).isEqualTo("10");
	}

	@Test
	@DisplayName("처리 순서 오케스트레이션 - 결제수단 입력 순서가 [CREDIT_CARD, Y_POINT]이든 관계없이 내부 채널인 Y_POINT가 항상 먼저 결제(charge)되는지 순서 검증")
	void chargeOrchestrationOrderTest() {
		// given
		UserWallet wallet = walletRepository.findById(USER_ID).orElseThrow();
		long currentBalance = wallet.getPointBalance();
		if (currentBalance < 50000L) {
			wallet.addPoint(50000L - currentBalance);
		}
		walletRepository.saveAndFlush(wallet);

		String idempotencyKey = UUID.randomUUID().toString();
		// 입력으로 카드 결제를 1번에 두고, 포인트를 2번에 둠
		BookingRequest.Payment paymentDto = new BookingRequest.Payment(159000L,
			List.of(
				new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 149000L, "card-token-123", null),
				new BookingRequest.Payment.Method(PaymentType.Y_POINT, 10000L, null, null)
			));
		BookingRequest request = new BookingRequest(PRODUCT_ID, paymentDto, "홍길동", "010-1234-5678");

		// when
		BookingResponse response = bookingService.createBooking(request, USER_ID, idempotencyKey);

		// then
		assertThat(response).isNotNull();

		// 1. Mockito InOrder를 통해 입력 순서에 무관하게 항상 Y_POINT 결제 승인(deduct)이 CREDIT_CARD 승인(authorize)보다 앞서 실행됨을 확증
		InOrder inOrder = inOrder(pointBalancePort, cardPgClient);
		inOrder.verify(pointBalancePort).deduct(eq(USER_ID), eq(10000L), eq(idempotencyKey));
		inOrder.verify(cardPgClient).authorize(any(PgAuthorizeRequest.class));
	}

	@Test
	@DisplayName("보상 중 부분 실패 - 환불 과정 도중 특정 결제 수단에서 취소 에러가 나더라도 다른 보상 환불과 Redis 재고 복구가 중단 없이 정상 처리되는지 검증")
	void partialCompensationFailureTest() {
		// given
		UserWallet wallet = walletRepository.findById(USER_ID).orElseThrow();
		long currentBalance = wallet.getPointBalance();
		if (currentBalance < 50000L) {
			wallet.addPoint(50000L - currentBalance);
		}
		walletRepository.saveAndFlush(wallet);

		// 1. 카드 결제(149,000원) 시 한도 초과 오류 모킹 (복구 보상 트랜잭션 유도)
		doReturn(PgResponse.declined("LIMIT_EXCEEDED", "결제 한도 초과"))
			.when(cardPgClient).authorize(argThat(req -> req.amount() == 149000L));

		// 2. 포인트 환불(restore) 과정에서 예외가 발생하도록 강제 스파이 스터빙 (보상 과정 중 부분 실패 연출)
		doThrow(new RuntimeException("포인트 환불 통신망 장애"))
			.when(pointBalancePort).restore(anyString(), eq(10000L));

		String idempotencyKey = UUID.randomUUID().toString();
		BookingRequest.Payment paymentDto = new BookingRequest.Payment(159000L,
			List.of(
				new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 149000L, "card-token-123", null),
				new BookingRequest.Payment.Method(PaymentType.Y_POINT, 10000L, null, null)
			));
		BookingRequest request = new BookingRequest(PRODUCT_ID, paymentDto, "홍길동", "010-1234-5678");

		// when & then: 예약은 여전히 실패(PaymentFailedException)해야 함
		assertThrows(PaymentFailedException.class, () -> {
			bookingService.createBooking(request, USER_ID, idempotencyKey);
		});

		// 3. 포인트 환불 과정이 실패했음에도 불구하고, Redis 재고 복구(increment)는 중간 실패에 가로막히지 않고 정상 완수되었는지 검증
		String stock = redisTemplate.opsForValue().get("stock:product:" + PRODUCT_ID);
		assertThat(stock).isEqualTo("10");

		// 4. 포인트 환불 메서드가 예외를 뚫고 실제로 호출은 진행되었었는지 검증
		verify(pointBalancePort).restore(anyString(), eq(10000L));
	}

	@Test
	@DisplayName("토큰 기반 한도초과 실패 검증 - 카드 토큰으로 'tok_decline_limit_exceeded'를 넘겼을 때 실제 결제가 거절되고 Saga 보상이 수행되는지 검증")
	void e2eFailureScenarioWithTokenTest() {
		// given
		UserWallet wallet = walletRepository.findById(USER_ID).orElseThrow();
		long currentBalance = wallet.getPointBalance();
		if (currentBalance < 50000L) {
			wallet.addPoint(50000L - currentBalance);
		}
		walletRepository.saveAndFlush(wallet);

		String idempotencyKey = UUID.randomUUID().toString();
		BookingRequest.Payment paymentDto = new BookingRequest.Payment(159000L,
			List.of(
				new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 149000L, "tok_decline_limit_exceeded", null),
				new BookingRequest.Payment.Method(PaymentType.Y_POINT, 10000L, null, null)
			));
		BookingRequest request = new BookingRequest(PRODUCT_ID, paymentDto, "홍길동", "010-1234-5678");

		// when & then: 한도 초과 에러가 발생해야 함
		assertThrows(PaymentFailedException.class, () -> {
			bookingService.createBooking(request, USER_ID, idempotencyKey);
		});

		// 1. 예약 미생성 검증
		assertThat(bookingRepository.findByIdempotencyKey(idempotencyKey)).isEmpty();

		// 2. 포인트 환불 검증 (원복 50000)
		UserWallet updatedWallet = walletRepository.findById(USER_ID).orElseThrow();
		assertThat(updatedWallet.getPointBalance()).isEqualTo(50000L);

		// 3. 재고 복구 검증
		String stock = redisTemplate.opsForValue().get("stock:product:" + PRODUCT_ID);
		assertThat(stock).isEqualTo("10");
	}
}

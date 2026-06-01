package com.stay.reservation.bookingpayment.booking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.stay.reservation.bookingpayment.booking.domain.BookingStatus;
import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;
import com.stay.reservation.bookingpayment.common.exception.DuplicateBookingException;
import com.stay.reservation.bookingpayment.common.exception.IdempotencyConflictException;
import com.stay.reservation.bookingpayment.common.exception.SoldOutException;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	@Mock
	private BookingService bookingService;

	@Mock
	private RedisStockManager redisStockManager;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@InjectMocks
	private BookingFacade bookingFacade;

	private BookingRequest validRequest;
	private BookingResponse mockResponse;

	@BeforeEach
	void setUp() {
		validRequest = new BookingRequest(
			1L,
			new BookingRequest.Payment(
				159000L,
				List.of(new BookingRequest.Payment.Method(PaymentType.Y_POINT, 159000L, null, null))
			),
			"홍길동",
			"010-1234-5678"
		);
		mockResponse = new BookingResponse(1234L, "B20261224-99999", BookingStatus.CONFIRMED, 159000L, java.time.LocalDateTime.now());
	}

	@Test
	@DisplayName("정상적인 최초 예약 생성 흐름 테스트")
	void success_bookingCreated() {
		when(bookingService.checkIdempotency("key-123")).thenReturn(null);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("idempotency:lock:key-123"), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
		when(redisStockManager.reserveStock(1L)).thenReturn(true);
		when(bookingService.proceedBookingTransaction(1001L, "key-123", validRequest)).thenReturn(mockResponse);

		BookingResponse response = bookingFacade.createBooking(1001L, "key-123", validRequest);

		assertNotNull(response);
		assertEquals("B20261224-99999", response.bookingNumber());
		assertEquals(BookingStatus.CONFIRMED, response.status());

		verify(redisTemplate).delete("idempotency:lock:key-123");
		verify(bookingService).proceedBookingTransaction(1001L, "key-123", validRequest);
		verify(redisStockManager, never()).rollbackStock(anyLong());
	}

	@Test
	@DisplayName("1차 방어선 작동 - 이미 완료된 멱등 키 유입 시 캐싱 응답 즉시 반환")
	void success_idempotencyFirstTierHit() {
		when(bookingService.checkIdempotency("key-123")).thenReturn(mockResponse);

		BookingResponse response = bookingFacade.createBooking(1001L, "key-123", validRequest);

		assertNotNull(response);
		assertEquals("B20261224-99999", response.bookingNumber());

		verify(redisTemplate, never()).opsForValue();
		verify(redisStockManager, never()).reserveStock(anyLong());
		verify(bookingService, never()).proceedBookingTransaction(anyLong(), anyString(), any(BookingRequest.class));
	}

	@Test
	@DisplayName("2차 방어선 작동 - 현재 처리 중인 동일 요청 시 조기 예외 반환 및 타인 락 미오염 검증")
	void fail_idempotencySecondTierCollision() {
		when(bookingService.checkIdempotency("key-123")).thenReturn(null);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("idempotency:lock:key-123"), eq("PROCESSING"), any(Duration.class))).thenReturn(false);

		assertThrows(IdempotencyConflictException.class, () -> bookingFacade.createBooking(1001L, "key-123", validRequest));

		verify(redisTemplate, never()).delete("idempotency:lock:key-123");
		verify(redisStockManager, never()).reserveStock(anyLong());
		verify(bookingService, never()).proceedBookingTransaction(anyLong(), anyString(), any(BookingRequest.class));
	}

	@Test
	@DisplayName("Redis Lua 스크립트 기반 재고 고갈 시 예외 반환 테스트")
	void fail_soldOutStock() {
		when(bookingService.checkIdempotency("key-123")).thenReturn(null);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("idempotency:lock:key-123"), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
		when(redisStockManager.reserveStock(1L)).thenReturn(false);

		assertThrows(SoldOutException.class, () -> bookingFacade.createBooking(1001L, "key-123", validRequest));

		verify(redisTemplate).delete("idempotency:lock:key-123");
		verify(bookingService, never()).proceedBookingTransaction(anyLong(), anyString(), any(BookingRequest.class));
		verify(redisStockManager, never()).rollbackStock(anyLong());
	}

	@Test
	@DisplayName("DB 저장 실패 시 Saga 보상 트랜잭션 정상 기동 검증")
	void fail_dbSaveSagaCompensation() {
		when(bookingService.checkIdempotency("key-123")).thenReturn(null);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("idempotency:lock:key-123"), eq("PROCESSING"), any(Duration.class))).thenReturn(true);
		when(redisStockManager.reserveStock(1L)).thenReturn(true);
		when(bookingService.proceedBookingTransaction(1001L, "key-123", validRequest)).thenThrow(new DataIntegrityViolationException("UNIQUE Constraint Violation"));

		assertThrows(DuplicateBookingException.class, () -> bookingFacade.createBooking(1001L, "key-123", validRequest));

		verify(redisStockManager).rollbackStock(1L);
		verify(redisTemplate).delete("idempotency:lock:key-123");
	}
}

package com.stay.reservation.bookingpayment.booking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

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
import org.springframework.data.redis.core.script.RedisScript;

import com.stay.reservation.bookingpayment.booking.domain.Booking;
import com.stay.reservation.bookingpayment.booking.domain.BookingStatus;
import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;
import com.stay.reservation.bookingpayment.booking.repository.BookingRepository;
import com.stay.reservation.bookingpayment.common.exception.DuplicateBookingException;
import com.stay.reservation.bookingpayment.common.exception.IdempotencyConflictException;
import com.stay.reservation.bookingpayment.common.exception.SoldOutException;
import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.product.repository.ProductRepository;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	@Mock
	private BookingRepository bookingRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private UserWalletRepository userWalletRepository;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@InjectMocks
	private BookingService bookingService;

	private BookingRequest validRequest;
	private Product mockProduct;

	@BeforeEach
	void setUp() {
		validRequest = new BookingRequest(1L, "홍길동", "010-1234-5678", 159000L);

		mockProduct = Product.builder()
			.id(1L)
			.roomTypeId(101L)
			.stayDate(LocalDate.of(2026, 12, 24))
			.price(159000L)
			.totalStock(10)
			.openAt(LocalDateTime.now().minusHours(1))
			.build();
	}

	@Test
	@DisplayName("정상적인 최초 예약 생성 흐름 테스트")
	void success_bookingCreated() {
		// given
		when(bookingRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.empty());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("idempotency:lock:key-123"), eq("PROCESSING"),
			any(Duration.class))).thenReturn(true);

		when(userWalletRepository.existsById(1001L)).thenReturn(true);
		when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));

		// Lua script 성공 (1L) 리턴 모킹
		when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(1L);

		Booking savedBooking = Booking.builder()
			.id(1234L)
			.bookingNumber("B20261224-99999")
			.idempotencyKey("key-123")
			.userId(1001L)
			.productId(1L)
			.totalAmount(159000L)
			.status(BookingStatus.CONFIRMED)
			.customerName("홍길동")
			.customerPhone("010-1234-5678")
			.build();
		when(bookingRepository.saveAndFlush(any(Booking.class))).thenReturn(savedBooking);

		// when
		BookingResponse response = bookingService.createBooking(1001L, "key-123", validRequest);

		// then
		assertNotNull(response);
		assertEquals("B20261224-99999", response.bookingNumber());
		assertEquals(BookingStatus.CONFIRMED, response.status());

		// Verification: 락 소멸 및 DB 적재 정상 확인
		verify(redisTemplate).delete("idempotency:lock:key-123");
		verify(bookingRepository).saveAndFlush(any(Booking.class));
		verify(valueOperations, never()).increment(anyString()); // Saga 보상 미실행 검증
	}

	@Test
	@DisplayName("1차 방어선(Application) 작동 - 이미 완료된 멱등 키 유입 시 캐싱 응답 즉시 반환")
	void success_idempotencyFirstTierHit() {
		// given
		Booking existingBooking = Booking.builder()
			.id(1234L)
			.bookingNumber("B20261224-11111")
			.idempotencyKey("key-123")
			.userId(1001L)
			.productId(1L)
			.totalAmount(159000L)
			.status(BookingStatus.CONFIRMED)
			.customerName("홍길동")
			.customerPhone("010-1234-5678")
			.build();
		when(bookingRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.of(existingBooking));

		// when
		BookingResponse response = bookingService.createBooking(1001L, "key-123", validRequest);

		// then
		assertNotNull(response);
		assertEquals("B20261224-11111", response.bookingNumber());

		// 1차 방어선에서 처리되었으므로 락을 잡거나 DB에 삽입하거나 레디스 재고를 깎지 않아야 함
		verify(redisTemplate, never()).opsForValue();
		verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
	}

	@Test
	@DisplayName("2차 방어선(Redis In-Flight Lock) 작동 - 현재 처리 중인 동일 요청 시 조기 예외 반환 및 타인 락 미오염 검증")
	void fail_idempotencySecondTierCollision() {
		// given
		when(bookingRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.empty());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		// 락 획득 실패 (false) 모킹
		when(valueOperations.setIfAbsent(eq("idempotency:lock:key-123"), eq("PROCESSING"),
			any(Duration.class))).thenReturn(false);

		// when & then
		assertThrows(IdempotencyConflictException.class,
			() -> bookingService.createBooking(1001L, "key-123", validRequest));

		// 타인의 락을 해제하지 않도록 delete(lockKey)가 절대 호출되지 않아야 함
		verify(redisTemplate, never()).delete("idempotency:lock:key-123");
		verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
	}

	@Test
	@DisplayName("Redis Lua 스크립트 기반 재고 고갈 시 예외 반환 테스트")
	void fail_soldOutLuaScript() {
		// given
		when(bookingRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.empty());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("idempotency:lock:key-123"), eq("PROCESSING"),
			any(Duration.class))).thenReturn(true);

		when(userWalletRepository.existsById(1001L)).thenReturn(true);
		when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));

		// Lua script 재고 없음 (-1L) 리턴 모킹
		when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(-1L);

		// when & then
		assertThrows(SoldOutException.class, () -> bookingService.createBooking(1001L, "key-123", validRequest));

		// 락은 정상 반환 확인
		verify(redisTemplate).delete("idempotency:lock:key-123");
		// DB 삽입 및 Saga 보상 미작동 확인
		verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
		verify(valueOperations, never()).increment(anyString());
	}

	@Test
	@DisplayName("DB 저장 실패 시 Saga 보상 트랜잭션 정상 기동 검증 (인메모리 재고 복구)")
	void fail_dbSaveSagaCompensation() {
		// given
		when(bookingRepository.findByIdempotencyKey("key-123")).thenReturn(Optional.empty());
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.setIfAbsent(eq("idempotency:lock:key-123"), eq("PROCESSING"),
			any(Duration.class))).thenReturn(true);

		when(userWalletRepository.existsById(1001L)).thenReturn(true);
		when(productRepository.findById(1L)).thenReturn(Optional.of(mockProduct));

		// Lua script 재고 선점 성공 (1L) 리턴 모킹
		when(redisTemplate.execute(any(RedisScript.class), anyList())).thenReturn(1L);

		// DB 저장 시 DataIntegrityViolationException 예외 던지기 모킹
		when(bookingRepository.saveAndFlush(any(Booking.class))).thenThrow(
			new DataIntegrityViolationException("UNIQUE Constraint Violation"));

		// when & then
		assertThrows(DuplicateBookingException.class,
			() -> bookingService.createBooking(1001L, "key-123", validRequest));

		// Saga 보상 트랜잭션 작동 확인: 재고 다시 1 증가(increment)
		verify(valueOperations).increment("stock:product:1");
		// 락 정상 반환 확인
		verify(redisTemplate).delete("idempotency:lock:key-123");
	}
}

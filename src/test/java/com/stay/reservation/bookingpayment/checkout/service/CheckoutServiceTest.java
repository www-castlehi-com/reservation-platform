package com.stay.reservation.bookingpayment.checkout.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.stay.reservation.bookingpayment.checkout.cache.CachedProductInfoCacheRepository;
import com.stay.reservation.bookingpayment.checkout.dto.CachedProductInfo;
import com.stay.reservation.bookingpayment.checkout.dto.CheckoutResponse;
import com.stay.reservation.bookingpayment.checkout.dto.ProductStatus;
import com.stay.reservation.bookingpayment.common.exception.ProductNotFoundException;
import com.stay.reservation.bookingpayment.common.exception.UserNotFoundException;
import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.product.repository.ProductRepository;
import com.stay.reservation.bookingpayment.roomtype.domain.RoomType;
import com.stay.reservation.bookingpayment.roomtype.repository.RoomTypeRepository;
import com.stay.reservation.bookingpayment.user.domain.UserWallet;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

	@Mock
	private ProductRepository productRepository;

	@Mock
	private RoomTypeRepository roomTypeRepository;

	@Mock
	private UserWalletRepository userWalletRepository;

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private CachedProductInfoCacheRepository cachedProductInfoCacheRepository;

	@InjectMocks
	private CheckoutService checkoutService;

	@Nested
	@DisplayName("getCheckout 조회 테스트")
	class GetCheckoutTest {

		private final Long productId = 1L;
		private final Long userId = 1001L;
		private final Long roomTypeId = 10L;

		@Test
		@DisplayName("성공: 캐시 Miss 발생 시, DB에서 조회하고 캐시에 저장한다")
		void success_cacheMiss() {
			// given
			// 1. Cache Miss 모킹
			when(cachedProductInfoCacheRepository.find(productId)).thenReturn(null);

			// 2. DB 조회 모킹
			Product mockProduct = Product.builder()
				.id(productId)
				.roomTypeId(roomTypeId)
				.stayDate(LocalDate.of(2026, 12, 24))
				.price(89000L)
				.totalStock(10)
				.openAt(LocalDateTime.now().minusHours(1))
				.build();
			when(productRepository.findById(productId)).thenReturn(Optional.of(mockProduct));

			RoomType mockRoomType = RoomType.builder()
				.id(roomTypeId)
				.title("신라호텔 서울 디럭스 더블룸")
				.originalPrice(250000L)
				.checkInTime(LocalTime.of(15, 0))
				.checkOutTime(LocalTime.of(11, 0))
				.build();
			when(roomTypeRepository.findById(roomTypeId)).thenReturn(Optional.of(mockRoomType));

			UserWallet mockWallet = UserWallet.builder().userId(userId).pointBalance(50000L).build();
			when(userWalletRepository.findById(userId)).thenReturn(Optional.of(mockWallet));

			// 3. Redis 재고 조회 모킹
			ValueOperations<String, String> mockOps = mock(ValueOperations.class);
			when(redisTemplate.opsForValue()).thenReturn(mockOps);
			when(mockOps.get("stock:product:" + productId)).thenReturn("7");

			// when
			CheckoutResponse response = checkoutService.getCheckout(productId, userId);

			// then
			// 응답 데이터 검증
			assertThat(response).isNotNull();
			assertThat(response.product().productId()).isEqualTo(productId);
			assertThat(response.product().title()).isEqualTo("신라호텔 서울 디럭스 더블룸");
			assertThat(response.product().remainingStock()).isEqualTo(7);
			assertThat(response.product().status()).isEqualTo(ProductStatus.OPEN);
			assertThat(response.userWallet().userId()).isEqualTo(userId);
			assertThat(response.userWallet().availablePoints()).isEqualTo(50000L);

			// 캐시 저장 및 DB 레포지토리 호출 검증
			verify(cachedProductInfoCacheRepository, times(1)).find(productId);
			verify(productRepository, times(1)).findById(productId);
			verify(roomTypeRepository, times(1)).findById(roomTypeId);
			verify(cachedProductInfoCacheRepository, times(1)).save(eq(productId), any(CachedProductInfo.class));
		}

		@Test
		@DisplayName("성공: 캐시 Hit 발생 시, DB(Product, RoomType)를 거치지 않고 캐시에서 반환한다")
		void success_cacheHit() {
			// given
			// 1. Cache Hit 모킹
			CachedProductInfo cachedInfo = new CachedProductInfo(productId, "신라호텔 서울 디럭스 더블룸", 250000L, 89000L,
				LocalDateTime.of(2026, 12, 24, 15, 0), LocalDateTime.of(2026, 12, 25, 11, 0),
				LocalDateTime.now().minusHours(1));
			when(cachedProductInfoCacheRepository.find(productId)).thenReturn(cachedInfo);

			// 2. 지갑 조회 모킹 (지갑은 매번 DB에서 실시간 조회)
			UserWallet mockWallet = UserWallet.builder().userId(userId).pointBalance(30000L).build();
			when(userWalletRepository.findById(userId)).thenReturn(Optional.of(mockWallet));

			// 3. Redis 재고 조회 모킹
			ValueOperations<String, String> mockOps = mock(ValueOperations.class);
			when(redisTemplate.opsForValue()).thenReturn(mockOps);
			when(mockOps.get("stock:product:" + productId)).thenReturn("5");

			// when
			CheckoutResponse response = checkoutService.getCheckout(productId, userId);

			// then
			// 응답 데이터 검증
			assertThat(response).isNotNull();
			assertThat(response.product().productId()).isEqualTo(productId);
			assertThat(response.product().title()).isEqualTo("신라호텔 서울 디럭스 더블룸");
			assertThat(response.product().remainingStock()).isEqualTo(5);
			assertThat(response.product().status()).isEqualTo(ProductStatus.OPEN);
			assertThat(response.userWallet().availablePoints()).isEqualTo(30000L);

			// DB(Product, RoomType) 미조회 및 캐시 미저장 검증
			verify(cachedProductInfoCacheRepository, times(1)).find(productId);
			verifyNoInteractions(productRepository);
			verifyNoInteractions(roomTypeRepository);
			verify(cachedProductInfoCacheRepository, never()).save(anyLong(), any());
		}

		@Test
		@DisplayName("예외: 존재하지 않는 상품 조회 시 예외를 던진다")
		void fail_productNotFound() {
			// given
			when(cachedProductInfoCacheRepository.find(productId)).thenReturn(null);
			when(productRepository.findById(productId)).thenReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> checkoutService.getCheckout(productId, userId)).isInstanceOf(
				ProductNotFoundException.class);
		}

		@Test
		@DisplayName("예외: 존재하지 않는 유저 조회 시 예외를 던진다")
		void fail_userNotFound() {
			// given
			CachedProductInfo cachedInfo = new CachedProductInfo(productId, "신라호텔 서울 디럭스 더블룸", 250000L, 89000L,
				LocalDateTime.of(2026, 12, 24, 15, 0), LocalDateTime.of(2026, 12, 25, 11, 0),
				LocalDateTime.now().minusHours(1));
			when(cachedProductInfoCacheRepository.find(productId)).thenReturn(cachedInfo);

			// redis ops mocking to prevent NPE
			ValueOperations<String, String> mockOps = mock(ValueOperations.class);
			when(redisTemplate.opsForValue()).thenReturn(mockOps);
			when(mockOps.get("stock:product:" + productId)).thenReturn("5");

			when(userWalletRepository.findById(userId)).thenReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> checkoutService.getCheckout(productId, userId)).isInstanceOf(
				UserNotFoundException.class);
		}
	}
}

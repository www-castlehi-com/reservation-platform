package com.stay.reservation.bookingpayment.checkout.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
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

import com.stay.reservation.bookingpayment.checkout.dto.CachedProductInfo;
import com.stay.reservation.bookingpayment.checkout.dto.CheckoutResponse;
import com.stay.reservation.bookingpayment.checkout.dto.ProductStatus;
import com.stay.reservation.bookingpayment.common.exception.UserNotFoundException;
import com.stay.reservation.bookingpayment.user.domain.UserWallet;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

	@Mock
	private CachedProductService cachedProductService;

	@Mock
	private UserWalletRepository userWalletRepository;

	@Mock
	private StringRedisTemplate redisTemplate;

	@InjectMocks
	private CheckoutService checkoutService;

	@Nested
	@DisplayName("getCheckout 조립 및 상태 연산 검증")
	class GetCheckoutTest {

		private final Long productId = 1L;
		private final Long userId = 1001L;

		@Test
		@DisplayName("성공: 상품 정보, 실시간 재고, 지갑 포인트를 합산하여 상태 OPEN 주문서를 정상 조립한다")
		void success_assembleCheckout_OpenStatus() {
			// given
			CachedProductInfo mockProductInfo = new CachedProductInfo(productId, "신라호텔 서울 디럭스 더블룸", 250000L, 89000L,
				LocalDateTime.of(2026, 12, 24, 15, 0), LocalDateTime.of(2026, 12, 25, 11, 0),
				LocalDateTime.now().minusHours(1));
			when(cachedProductService.getProductInfo(productId)).thenReturn(mockProductInfo);

			UserWallet mockWallet = UserWallet.builder().userId(userId).pointBalance(50000L).build();
			when(userWalletRepository.findById(userId)).thenReturn(Optional.of(mockWallet));

			ValueOperations<String, String> mockOps = mock(ValueOperations.class);
			when(redisTemplate.opsForValue()).thenReturn(mockOps);
			when(mockOps.get("stock:product:" + productId)).thenReturn("5");

			// when
			CheckoutResponse response = checkoutService.getCheckout(productId, userId);

			// then
			assertThat(response).isNotNull();
			assertThat(response.product().productId()).isEqualTo(productId);
			assertThat(response.product().remainingStock()).isEqualTo(5);
			assertThat(response.product().status()).isEqualTo(ProductStatus.OPEN);
			assertThat(response.userWallet().availablePoints()).isEqualTo(50000L);
		}

		@Test
		@DisplayName("성공: 재고가 0 이하일 때 SOLD_OUT 상태로 주문서를 조립한다")
		void success_assembleCheckout_SoldOutStatus() {
			// given
			CachedProductInfo mockProductInfo = new CachedProductInfo(productId, "신라호텔 서울 디럭스 더블룸", 250000L, 89000L,
				LocalDateTime.of(2026, 12, 24, 15, 0), LocalDateTime.of(2026, 12, 25, 11, 0),
				LocalDateTime.now().minusHours(1));
			when(cachedProductService.getProductInfo(productId)).thenReturn(mockProductInfo);

			UserWallet mockWallet = UserWallet.builder().userId(userId).pointBalance(50000L).build();
			when(userWalletRepository.findById(userId)).thenReturn(Optional.of(mockWallet));

			ValueOperations<String, String> mockOps = mock(ValueOperations.class);
			when(redisTemplate.opsForValue()).thenReturn(mockOps);
			when(mockOps.get("stock:product:" + productId)).thenReturn("0");

			// when
			CheckoutResponse response = checkoutService.getCheckout(productId, userId);

			// then
			assertThat(response.product().remainingStock()).isEqualTo(0);
			assertThat(response.product().status()).isEqualTo(ProductStatus.SOLD_OUT);
		}

		@Test
		@DisplayName("예외: 존재하지 않는 유저 조회 시 예외를 던진다")
		void fail_userNotFound() {
			// given
			CachedProductInfo mockProductInfo = new CachedProductInfo(productId, "신라호텔 서울 디럭스 더블룸", 250000L, 89000L,
				LocalDateTime.of(2026, 12, 24, 15, 0), LocalDateTime.of(2026, 12, 25, 11, 0),
				LocalDateTime.now().minusHours(1));
			when(cachedProductService.getProductInfo(productId)).thenReturn(mockProductInfo);

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

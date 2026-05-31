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

import com.stay.reservation.bookingpayment.checkout.cache.CachedProductInfoCacheRepository;
import com.stay.reservation.bookingpayment.checkout.dto.CachedProductInfo;
import com.stay.reservation.bookingpayment.common.exception.ProductNotFoundException;
import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.product.repository.ProductRepository;
import com.stay.reservation.bookingpayment.roomtype.domain.RoomType;
import com.stay.reservation.bookingpayment.roomtype.repository.RoomTypeRepository;

@ExtendWith(MockitoExtension.class)
class CachedProductServiceTest {

	@Mock
	private CachedProductInfoCacheRepository cachedProductInfoCacheRepository;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private RoomTypeRepository roomTypeRepository;

	@InjectMocks
	private CachedProductService cachedProductService;

	@Nested
	@DisplayName("getProductInfo 조회 및 캐시 분기 검증")
	class GetProductInfoTest {

		private final Long productId = 1L;
		private final Long roomTypeId = 10L;

		@Test
		@DisplayName("성공: 캐시 Hit 발생 시, DB 조회 없이 캐싱된 상품 정보를 반환한다")
		void success_cacheHit() {
			// given
			CachedProductInfo cachedInfo = new CachedProductInfo(productId, "신라호텔 서울 디럭스 더블룸", 250000L, 89000L,
				LocalDateTime.of(2026, 12, 24, 15, 0), LocalDateTime.of(2026, 12, 25, 11, 0),
				LocalDateTime.now().minusHours(1));
			when(cachedProductInfoCacheRepository.find(productId)).thenReturn(cachedInfo);

			// when
			CachedProductInfo result = cachedProductService.getProductInfo(productId);

			// then
			assertThat(result).isNotNull();
			assertThat(result.productId()).isEqualTo(productId);
			assertThat(result.title()).isEqualTo("신라호텔 서울 디럭스 더블룸");

			// DB 호출 없음 및 캐시 갱신 없음 검증
			verify(cachedProductInfoCacheRepository, times(1)).find(productId);
			verifyNoInteractions(productRepository);
			verifyNoInteractions(roomTypeRepository);
			verify(cachedProductInfoCacheRepository, never()).save(anyLong(), any());
		}

		@Test
		@DisplayName("성공: 캐시 Miss 발생 시, DB에서 조회해 조립한 뒤 캐시에 저장하고 반환한다")
		void success_cacheMiss() {
			// given
			when(cachedProductInfoCacheRepository.find(productId)).thenReturn(null);

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

			// when
			CachedProductInfo result = cachedProductService.getProductInfo(productId);

			// then
			assertThat(result).isNotNull();
			assertThat(result.productId()).isEqualTo(productId);
			assertThat(result.title()).isEqualTo("신라호텔 서울 디럭스 더블룸");

			// DB 조회 및 캐시 갱신 검증
			verify(cachedProductInfoCacheRepository, times(1)).find(productId);
			verify(productRepository, times(1)).findById(productId);
			verify(roomTypeRepository, times(1)).findById(roomTypeId);
			verify(cachedProductInfoCacheRepository, times(1)).save(eq(productId), any(CachedProductInfo.class));
		}

		@Test
		@DisplayName("예외: 캐시 Miss 상태에서 존재하지 않는 상품을 DB 조회 시 예외를 던진다")
		void fail_productNotFound() {
			// given
			when(cachedProductInfoCacheRepository.find(productId)).thenReturn(null);
			when(productRepository.findById(productId)).thenReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> cachedProductService.getProductInfo(productId)).isInstanceOf(
				ProductNotFoundException.class);
		}
	}
}

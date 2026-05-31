package com.stay.reservation.bookingpayment.checkout.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.checkout.cache.CachedProductInfoCacheRepository;
import com.stay.reservation.bookingpayment.checkout.dto.CachedProductInfo;
import com.stay.reservation.bookingpayment.checkout.dto.CheckoutResponse;
import com.stay.reservation.bookingpayment.checkout.dto.ProductCheckoutInfo;
import com.stay.reservation.bookingpayment.checkout.dto.ProductStatus;
import com.stay.reservation.bookingpayment.checkout.dto.UserWalletInfo;
import com.stay.reservation.bookingpayment.common.exception.ProductNotFoundException;
import com.stay.reservation.bookingpayment.common.exception.UserNotFoundException;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;
import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.product.repository.ProductRepository;
import com.stay.reservation.bookingpayment.roomtype.domain.RoomType;
import com.stay.reservation.bookingpayment.roomtype.repository.RoomTypeRepository;
import com.stay.reservation.bookingpayment.user.domain.UserWallet;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

	private final ProductRepository productRepository;
	private final RoomTypeRepository roomTypeRepository;
	private final UserWalletRepository userWalletRepository;
	private final StringRedisTemplate redisTemplate;
	private final CachedProductInfoCacheRepository cachedProductInfoCacheRepository;

	@Transactional(readOnly = true)
	public CheckoutResponse getCheckout(Long productId, Long userId) {
		CachedProductInfo cachedProductInfo = getCachedProductInfo(productId);
		int remainingStock = getRemainingStock(productId);

		UserWallet wallet = userWalletRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

		ProductStatus status = computeStatus(cachedProductInfo.openAt(), remainingStock);

		ProductCheckoutInfo productCheckoutInfo = ProductCheckoutInfo.of(cachedProductInfo, remainingStock, status);
		UserWalletInfo walletInfo = UserWalletInfo.from(wallet);

		return new CheckoutResponse(productCheckoutInfo, walletInfo, List.of(PaymentType.values()));
	}

	private CachedProductInfo getCachedProductInfo(Long productId) {
		CachedProductInfo cached = cachedProductInfoCacheRepository.find(productId);
		if (cached != null) {
			log.info("ProductInfo cache hit for productId: {}", productId);
			return cached;
		}

		log.info("ProductInfo cache miss for productId: {}", productId);
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new ProductNotFoundException(productId));

		RoomType roomType = roomTypeRepository.findById(product.getRoomTypeId())
			.orElseThrow(() -> new IllegalStateException("RoomType not found for product " + productId));

		CachedProductInfo info = CachedProductInfo.from(product, roomType);
		cachedProductInfoCacheRepository.save(productId, info);
		return info;
	}

	private int getRemainingStock(Long productId) {
		String key = "stock:product:" + productId;
		String value = redisTemplate.opsForValue().get(key);
		return value == null ? 0 : Integer.parseInt(value);
	}

	private ProductStatus computeStatus(LocalDateTime openAt, int stock) {
		if (LocalDateTime.now().isBefore(openAt)) {
			return ProductStatus.UPCOMING;
		}
		if (stock <= 0) {
			return ProductStatus.SOLD_OUT;
		}
		return ProductStatus.OPEN;
	}
}

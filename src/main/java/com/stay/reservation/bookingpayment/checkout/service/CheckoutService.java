package com.stay.reservation.bookingpayment.checkout.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.checkout.dto.CheckoutResponse;
import com.stay.reservation.bookingpayment.checkout.dto.ProductInfo;
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

@Service
@RequiredArgsConstructor
public class CheckoutService {

	private final ProductRepository productRepository;
	private final RoomTypeRepository roomTypeRepository;
	private final UserWalletRepository userWalletRepository;
	private final StringRedisTemplate redisTemplate;

	@Transactional(readOnly = true)
	public CheckoutResponse getCheckout(Long productId, Long userId) {
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new ProductNotFoundException(productId));

		RoomType roomType = roomTypeRepository.findById(product.getRoomTypeId())
			.orElseThrow(() -> new IllegalStateException("RoomType not found for product " + productId));

		UserWallet wallet = userWalletRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

		int remainingStock = getRemainingStock(productId);

		ProductStatus status = computeStatus(product.getOpenAt(), remainingStock);

		ProductInfo productInfo = ProductInfo.from(product, roomType, remainingStock, status);
		UserWalletInfo walletInfo = UserWalletInfo.from(wallet);

		return new CheckoutResponse(productInfo, walletInfo, List.of(PaymentType.values()));
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

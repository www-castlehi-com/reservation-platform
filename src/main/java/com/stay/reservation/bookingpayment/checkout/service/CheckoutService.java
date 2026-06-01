package com.stay.reservation.bookingpayment.checkout.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.booking.service.RedisStockManager;
import com.stay.reservation.bookingpayment.checkout.dto.CachedProductInfo;
import com.stay.reservation.bookingpayment.checkout.dto.CheckoutResponse;
import com.stay.reservation.bookingpayment.checkout.dto.ProductCheckoutInfo;
import com.stay.reservation.bookingpayment.checkout.dto.ProductStatus;
import com.stay.reservation.bookingpayment.checkout.dto.UserWalletInfo;
import com.stay.reservation.bookingpayment.common.exception.UserNotFoundException;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;
import com.stay.reservation.bookingpayment.user.domain.UserWallet;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CheckoutService {

	private final CachedProductService cachedProductService;
	private final UserWalletRepository userWalletRepository;
	private final RedisStockManager redisStockManager;

	@Transactional(readOnly = true)
	public CheckoutResponse getCheckout(Long productId, Long userId) {
		CachedProductInfo cachedProductInfo = cachedProductService.getProductInfo(productId);
		int remainingStock = redisStockManager.getStock(productId);

		UserWallet wallet = userWalletRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

		ProductStatus status = computeStatus(cachedProductInfo.openAt(), remainingStock);

		ProductCheckoutInfo productCheckoutInfo = ProductCheckoutInfo.of(cachedProductInfo, remainingStock, status);
		UserWalletInfo walletInfo = UserWalletInfo.from(wallet);

		return new CheckoutResponse(productCheckoutInfo, walletInfo, List.of(PaymentType.values()));
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

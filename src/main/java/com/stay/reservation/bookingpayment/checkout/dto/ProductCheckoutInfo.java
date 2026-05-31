package com.stay.reservation.bookingpayment.checkout.dto;

import java.time.LocalDateTime;

public record ProductCheckoutInfo(Long productId, String title, Long originalPrice, Long price,
								  LocalDateTime checkInTime, LocalDateTime checkOutTime, Integer remainingStock,
								  LocalDateTime openAt, ProductStatus status) {

	public static ProductCheckoutInfo of(CachedProductInfo info, Integer remainingStock, ProductStatus status) {
		return new ProductCheckoutInfo(info.productId(), info.title(), info.originalPrice(), info.price(),
			info.checkInTime(), info.checkOutTime(), remainingStock, info.openAt(), status);
	}
}

package com.stay.reservation.bookingpayment.checkout.dto;

import java.time.LocalDateTime;

import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.roomtype.domain.RoomType;

public record CachedProductInfo(Long productId, String title, Long originalPrice, Long price, LocalDateTime checkInTime,
								LocalDateTime checkOutTime, LocalDateTime openAt) {

	public static CachedProductInfo from(Product product, RoomType roomType) {
		LocalDateTime checkIn = LocalDateTime.of(product.getStayDate(), roomType.getCheckInTime());
		LocalDateTime checkOut = LocalDateTime.of(product.getStayDate().plusDays(1), roomType.getCheckOutTime());

		return new CachedProductInfo(product.getId(), roomType.getTitle(), roomType.getOriginalPrice(),
			product.getPrice(), checkIn, checkOut, product.getOpenAt());
	}
}

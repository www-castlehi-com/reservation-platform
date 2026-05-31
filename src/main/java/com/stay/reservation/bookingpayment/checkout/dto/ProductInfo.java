package com.stay.reservation.bookingpayment.checkout.dto;

import java.time.LocalDateTime;

import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.roomtype.domain.RoomType;

public record ProductInfo(Long productId, String title, Long originalPrice, Long price, LocalDateTime checkInTime,
						  LocalDateTime checkOutTime, Integer remainingStock, LocalDateTime openAt,
						  ProductStatus status) {

	public static ProductInfo from(Product product, RoomType roomType, int stock, ProductStatus status) {
		LocalDateTime checkIn = LocalDateTime.of(product.getStayDate(), roomType.getCheckInTime());
		LocalDateTime checkOut = LocalDateTime.of(product.getStayDate().plusDays(1), roomType.getCheckOutTime());

		return new ProductInfo(product.getId(), roomType.getTitle(), roomType.getOriginalPrice(), product.getPrice(),
			checkIn, checkOut, stock, product.getOpenAt(), status);
	}
}

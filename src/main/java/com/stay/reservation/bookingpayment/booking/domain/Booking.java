package com.stay.reservation.bookingpayment.booking.domain;

import com.stay.reservation.bookingpayment.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bookings", indexes = {@Index(name = "idx_bookings_user_id", columnList = "user_id"),
	@Index(name = "idx_bookings_product_id", columnList = "product_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Booking extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String bookingNumber;

	@Column(nullable = false, unique = true, length = 64)
	private String idempotencyKey;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Long productId;

	@Column(nullable = false)
	private Long totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status;

	@Column(nullable = false, length = 50)
	private String customerName;

	@Column(nullable = false, length = 20)
	private String customerPhone;
}

package com.stay.reservation.bookingpayment.product.domain;

import java.time.LocalDateTime;

import com.stay.reservation.bookingpayment.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Product extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private Long originalPrice;

	@Column(nullable = false)
	private Long discountPrice;

	@Column(nullable = false)
	private Integer totalStock;

	@Column(nullable = false)
	private LocalDateTime checkInTime;

	@Column(nullable = false)
	private LocalDateTime checkOutTime;

	@Column(nullable = false)
	private LocalDateTime openAt;
}

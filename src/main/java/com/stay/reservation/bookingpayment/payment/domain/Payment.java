package com.stay.reservation.bookingpayment.payment.domain;

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
@Table(name = "payments", indexes = {@Index(name = "idx_payments_booking_id", columnList = "booking_id"),
	@Index(name = "idx_payments_method_type_status", columnList = "method_type, status")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long bookingId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentMethodType methodType;

	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(length = 100)
	private String transactionId;

	@Column(columnDefinition = "TEXT")
	private String cardInfo;

	@Column(length = 200)
	private String failReason;
}

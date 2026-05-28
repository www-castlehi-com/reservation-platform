package com.stay.reservation.bookingpayment.payment.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "payment_histories", indexes = {
	@Index(name = "idx_payment_histories_payment_id", columnList = "payment_id"),
	@Index(name = "idx_payment_histories_step_created_at", columnList = "step, created_at")})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PaymentHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long paymentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStep step;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(length = 255)
	private String reason;

	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;
}

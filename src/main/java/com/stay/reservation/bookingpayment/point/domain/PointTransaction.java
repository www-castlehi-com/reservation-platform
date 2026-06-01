package com.stay.reservation.bookingpayment.point.domain;

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
@Table(name = "point_transactions", indexes = {
	@Index(name = "idx_point_transactions_user_created", columnList = "user_id, created_at"),
	@Index(name = "idx_point_transactions_booking_id", columnList = "booking_id")})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PointTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	private Long bookingId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PointTransactionType type;

	@Column(nullable = false)
	private Long amount;

	@Column(nullable = false)
	private Long balanceBefore;

	@Column(nullable = false)
	private Long balanceAfter;

	@Column(length = 64)
	private String idempotencyKey;

	private Long originalTransactionId;

	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;
}

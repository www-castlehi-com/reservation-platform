package com.stay.reservation.bookingpayment.idempotency.domain;

import com.stay.reservation.bookingpayment.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BookingIdempotency extends BaseTimeEntity {

	@Id
	@Column(length = 64)
	private String idempotencyKey;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, length = 64)
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IdempotencyStatus status;

	private Long bookingId;

	@Column(columnDefinition = "TEXT")
	private String responseBody;

	@Column(length = 200)
	private String failReason;
}

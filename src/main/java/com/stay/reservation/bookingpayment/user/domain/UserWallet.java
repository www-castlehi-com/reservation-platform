package com.stay.reservation.bookingpayment.user.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.stay.reservation.bookingpayment.payment.exception.InsufficientPointException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_wallets")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserWallet {

	@Id
	private Long userId;

	@Builder.Default
	@Column(nullable = false)
	private Long pointBalance = 0L;

	@Version
	@Builder.Default
	@Column(nullable = false)
	private Long version = 0L;

	@LastModifiedDate
	private LocalDateTime updatedAt;

	public void deductPoint(long amount) {
		if (this.pointBalance < amount) {
			throw new InsufficientPointException(this.userId, this.pointBalance, amount);
		}
		this.pointBalance -= amount;
	}

	public void addPoint(long amount) {
		this.pointBalance += amount;
	}
}

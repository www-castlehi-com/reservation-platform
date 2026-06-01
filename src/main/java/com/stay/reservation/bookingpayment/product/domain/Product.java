package com.stay.reservation.bookingpayment.product.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.stay.reservation.bookingpayment.common.domain.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(name = "ux_products_room_date", columnNames = {
	"room_type_id", "stay_date"}), indexes = {@Index(name = "idx_products_room_type_id", columnList = "room_type_id"),
	@Index(name = "idx_products_open_at", columnList = "open_at")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Product extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long roomTypeId;

	@Column(nullable = false)
	private LocalDate stayDate;

	@Column(nullable = false)
	private Long price;

	@Column(nullable = false)
	private Integer totalStock;

	@Column(nullable = false)
	private LocalDateTime openAt;

	public void decreaseStock() {
		this.totalStock--;
	}
}

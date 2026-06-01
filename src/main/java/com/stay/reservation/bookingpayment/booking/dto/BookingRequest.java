package com.stay.reservation.bookingpayment.booking.dto;

import java.util.List;

import com.stay.reservation.bookingpayment.payment.model.PaymentType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingRequest(@NotNull(message = "상품 ID는 필수입니다.") Long productId,
							 @NotNull(message = "결제 정보는 필수입니다.") @Valid Payment payment,
							 @NotBlank(message = "예약자 이름은 필수입니다.") String customerName,
							 @NotBlank(message = "예약자 전화번호는 필수입니다.") String customerPhone) {

	public record Payment(
		@NotNull(message = "총 결제 금액은 필수입니다.") @Positive(message = "총 결제 금액은 양수여야 합니다.") Long totalAmount,
		@NotEmpty(message = "결제 수단은 최소 1개 이상이어야 합니다.") @Valid List<Method> methods) {

		public record Method(@NotNull(message = "결제 타입은 필수입니다.") PaymentType type,
							 @NotNull(message = "결제 금액은 필수입니다.") @Positive(message = "결제 금액은 양수여야 합니다.") Long amount,
							 String cardToken, String ypayToken) {

		}
	}
}

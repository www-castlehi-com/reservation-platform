package com.stay.reservation.bookingpayment.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingRequest(@NotNull(message = "상품 ID는 필수입니다.") Long productId,
							 @NotBlank(message = "예약자 이름은 필수입니다.") String customerName,
							 @NotBlank(message = "예약자 전화번호는 필수입니다.") String customerPhone,
							 @NotNull(message = "총 결제 금액은 필수입니다.") @Positive(message = "총 결제 금액은 양수여야 합니다.") Long totalAmount) {

}

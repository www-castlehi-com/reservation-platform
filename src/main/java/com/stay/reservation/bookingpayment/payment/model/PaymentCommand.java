package com.stay.reservation.bookingpayment.payment.model;

public record PaymentCommand(String idempotencyKey, long userId, PaymentType paymentType, long amount,
							 PaymentDetail paymentDetail) {

	public PaymentCommand {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("idempotencyKey는 필수입니다.");
		}
		if (amount <= 0) {
			throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다. amount=" + amount);
		}
		if (paymentType == null) {
			throw new IllegalArgumentException("paymentType은 필수입니다.");
		}
		if (paymentDetail == null) {
			throw new IllegalArgumentException("paymentDetail은 필수입니다.");
		}
		validateTypeCompatibility(paymentType, paymentDetail);
	}

	private static void validateTypeCompatibility(PaymentType paymentType, PaymentDetail paymentDetail) {
		boolean isCompatible = switch (paymentType) {
			case CREDIT_CARD -> paymentDetail instanceof PaymentDetail.CardPaymentDetail;
			case Y_PAY -> paymentDetail instanceof PaymentDetail.YPayPaymentDetail;
			case Y_POINT -> paymentDetail instanceof PaymentDetail.PointPaymentDetail;
		};
		if (!isCompatible) {
			throw new IllegalArgumentException(String.format("결제 수단 타입(%s)과 입력된 세부 정보(%s)의 타입이 일치하지 않습니다.", paymentType,
				paymentDetail.getClass().getSimpleName()));
		}
	}
}

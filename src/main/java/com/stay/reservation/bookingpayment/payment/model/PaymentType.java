package com.stay.reservation.bookingpayment.payment.model;

public enum PaymentType {

	CREDIT_CARD(PaymentChannel.EXTERNAL),

	Y_PAY(PaymentChannel.EXTERNAL),

	Y_POINT(PaymentChannel.INTERNAL);

	private final PaymentChannel paymentChannel;

	PaymentType(PaymentChannel paymentChannel) {
		this.paymentChannel = paymentChannel;
	}

	public PaymentChannel getPaymentChannel() {
		return paymentChannel;
	}

	public boolean isExternalChannel() {
		return paymentChannel == PaymentChannel.EXTERNAL;
	}

	public enum PaymentChannel {
		INTERNAL, EXTERNAL
	}
}

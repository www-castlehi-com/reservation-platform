package com.stay.reservation.bookingpayment.payment.model;

public sealed interface PaymentDetail
	permits PaymentDetail.CardPaymentDetail, PaymentDetail.YPayPaymentDetail, PaymentDetail.PointPaymentDetail {

	record CardPaymentDetail(String cardToken) implements PaymentDetail {

		public CardPaymentDetail {
			if (cardToken == null || cardToken.isBlank()) {
				throw new IllegalArgumentException("cardToken은 필수입니다.");
			}
		}
	}

	record YPayPaymentDetail(String yPayToken) implements PaymentDetail {

		public YPayPaymentDetail {
			if (yPayToken == null || yPayToken.isBlank()) {
				throw new IllegalArgumentException("yPayToken은 필수입니다.");
			}
		}
	}

	record PointPaymentDetail() implements PaymentDetail {

	}
}

package com.stay.reservation.bookingpayment.checkout.dto;

import java.util.List;

import com.stay.reservation.bookingpayment.payment.model.PaymentType;

public record CheckoutResponse(ProductInfo product, UserWalletInfo userWallet,
							   List<PaymentType> supportedPaymentMethods) {

}

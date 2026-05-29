package com.stay.reservation.bookingpayment.payment.service;

import com.stay.reservation.bookingpayment.payment.model.PaymentCommand;
import com.stay.reservation.bookingpayment.payment.model.PaymentResult;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;

public interface PaymentMethod {

	PaymentType getPaymentType();

	PaymentResult charge(PaymentCommand paymentCommand);

	PaymentResult refund(String transactionId, long amount);
}

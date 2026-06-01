package com.stay.reservation.bookingpayment.booking.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.payment.model.PaymentCommand;
import com.stay.reservation.bookingpayment.payment.model.PaymentDetail;

@Component
public class PaymentCommandMapper {

	public List<PaymentCommand> toCommands(BookingRequest.Payment payment, Long userId, String idempotencyKey) {
		return payment.methods().stream().map(method -> toCommand(method, userId, idempotencyKey)).toList();
	}

	private PaymentCommand toCommand(BookingRequest.Payment.Method method, Long userId, String idempotencyKey) {
		PaymentDetail detail = switch (method.type()) {
			case CREDIT_CARD -> new PaymentDetail.CardPaymentDetail(method.cardToken());
			case Y_PAY -> new PaymentDetail.YPayPaymentDetail(method.ypayToken());
			case Y_POINT -> new PaymentDetail.PointPaymentDetail();
		};

		return new PaymentCommand(idempotencyKey, userId, method.type(), method.amount(), detail);
	}
}

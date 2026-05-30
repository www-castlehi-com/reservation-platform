package com.stay.reservation.bookingpayment.payment.infra;

import org.springframework.stereotype.Component;

import com.stay.reservation.bookingpayment.payment.exception.InsufficientPointException;
import com.stay.reservation.bookingpayment.payment.model.PaymentCommand;
import com.stay.reservation.bookingpayment.payment.model.PaymentDetail;
import com.stay.reservation.bookingpayment.payment.model.PaymentResult;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;
import com.stay.reservation.bookingpayment.payment.port.PointBalancePort;
import com.stay.reservation.bookingpayment.payment.service.PaymentMethod;

@Component
public class PointPaymentMethod implements PaymentMethod {

	private final PointBalancePort pointBalancePort;

	public PointPaymentMethod(PointBalancePort pointBalancePort) {
		this.pointBalancePort = pointBalancePort;
	}

	@Override
	public PaymentType getPaymentType() {
		return PaymentType.Y_POINT;
	}

	@Override
	public PaymentResult charge(PaymentCommand paymentCommand) {
		if (!(paymentCommand.paymentDetail() instanceof PaymentDetail.PointPaymentDetail)) {
			throw new IllegalArgumentException(
				"포인트 결제에는 PointPaymentDetail이 필요합니다. " + "실제 타입=" + paymentCommand.paymentDetail()
					.getClass()
					.getSimpleName());
		}

		try {
			String pointTransactionId = pointBalancePort.deduct(paymentCommand.userId(), paymentCommand.amount(),
				paymentCommand.idempotencyKey());
			return PaymentResult.success(PaymentType.Y_POINT, paymentCommand.amount(), pointTransactionId);
		} catch (InsufficientPointException insufficientPointException) {
			return PaymentResult.failure(PaymentType.Y_POINT, paymentCommand.amount(),
				insufficientPointException.getMessage());
		}
	}

	@Override
	public PaymentResult refund(String transactionId, long amount) {
		String restoreTransactionId = pointBalancePort.restore(transactionId, amount);
		return PaymentResult.success(PaymentType.Y_POINT, amount, restoreTransactionId);
	}
}

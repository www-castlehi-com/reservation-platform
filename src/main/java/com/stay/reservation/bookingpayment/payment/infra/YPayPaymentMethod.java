package com.stay.reservation.bookingpayment.payment.infra;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.stay.reservation.bookingpayment.payment.model.PaymentCommand;
import com.stay.reservation.bookingpayment.payment.model.PaymentDetail;
import com.stay.reservation.bookingpayment.payment.model.PaymentResult;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;
import com.stay.reservation.bookingpayment.payment.port.pg.PgAuthorizeRequest;
import com.stay.reservation.bookingpayment.payment.port.pg.PgClient;
import com.stay.reservation.bookingpayment.payment.port.pg.PgResponse;
import com.stay.reservation.bookingpayment.payment.service.PaymentMethod;

@Component
public class YPayPaymentMethod implements PaymentMethod {

	private final PgClient yPayPgClient;

	public YPayPaymentMethod(@Qualifier("yPayPgClient") PgClient yPayPgClient) {
		this.yPayPgClient = yPayPgClient;
	}

	@Override
	public PaymentType getPaymentType() {
		return PaymentType.Y_PAY;
	}

	@Override
	public PaymentResult charge(PaymentCommand paymentCommand) {
		if (!(paymentCommand.paymentDetail() instanceof PaymentDetail.YPayPaymentDetail yPayPaymentDetail)) {
			throw new IllegalArgumentException(
				"Y페이 결제에는 YPayPaymentDetail이 필요합니다. " + "실제 타입=" + paymentCommand.paymentDetail()
					.getClass()
					.getSimpleName());
		}

		PgAuthorizeRequest pgAuthorizeRequest = new PgAuthorizeRequest(paymentCommand.idempotencyKey(),
			yPayPaymentDetail.yPayToken(), paymentCommand.amount());

		PgResponse pgResponse = yPayPgClient.authorize(pgAuthorizeRequest);

		if (!pgResponse.approved()) {
			return PaymentResult.failure(PaymentType.Y_PAY, paymentCommand.amount(), pgResponse.failureMessage());
		}

		return PaymentResult.success(PaymentType.Y_PAY, paymentCommand.amount(), pgResponse.pgTransactionId());
	}

	@Override
	public PaymentResult refund(String transactionId, long amount) {
		PgResponse pgResponse = yPayPgClient.cancel(transactionId, amount);
		if (!pgResponse.approved()) {
			return PaymentResult.failure(PaymentType.Y_PAY, amount, pgResponse.failureMessage());
		}
		return PaymentResult.success(PaymentType.Y_PAY, amount, pgResponse.pgTransactionId());
	}
}

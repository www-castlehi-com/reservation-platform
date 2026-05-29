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
public class CreditCardPaymentMethod implements PaymentMethod {

	private final PgClient cardPgClient;

	public CreditCardPaymentMethod(@Qualifier("cardPgClient") PgClient cardPgClient) {
		this.cardPgClient = cardPgClient;
	}

	@Override
	public PaymentType getPaymentType() {
		return PaymentType.CREDIT_CARD;
	}

	@Override
	public PaymentResult charge(PaymentCommand paymentCommand) {
		if (!(paymentCommand.paymentDetail() instanceof PaymentDetail.CardPaymentDetail cardPaymentDetail)) {
			throw new IllegalArgumentException(
				"신용카드 결제에는 CardPaymentDetail이 필요합니다. " + "실제 타입=" + paymentCommand.paymentDetail()
					.getClass()
					.getSimpleName());
		}

		PgAuthorizeRequest pgAuthorizeRequest = new PgAuthorizeRequest(paymentCommand.idempotencyKey(),
			cardPaymentDetail.cardToken(), paymentCommand.amount());

		PgResponse pgResponse = cardPgClient.authorize(pgAuthorizeRequest);

		if (!pgResponse.approved()) {
			return PaymentResult.failure(PaymentType.CREDIT_CARD, paymentCommand.amount(), pgResponse.failureMessage());
		}

		return PaymentResult.success(PaymentType.CREDIT_CARD, paymentCommand.amount(), pgResponse.pgTransactionId());
	}

	@Override
	public PaymentResult refund(String transactionId, long amount) {
		PgResponse pgResponse = cardPgClient.cancel(transactionId, amount);
		if (!pgResponse.approved()) {
			return PaymentResult.failure(PaymentType.CREDIT_CARD, amount, pgResponse.failureMessage());
		}
		return PaymentResult.success(PaymentType.CREDIT_CARD, amount, pgResponse.pgTransactionId());
	}
}

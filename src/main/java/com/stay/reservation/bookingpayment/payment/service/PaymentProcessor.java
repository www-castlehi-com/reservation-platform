package com.stay.reservation.bookingpayment.payment.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.stay.reservation.bookingpayment.payment.model.CompositePaymentResult;
import com.stay.reservation.bookingpayment.payment.model.PaymentCommand;
import com.stay.reservation.bookingpayment.payment.model.PaymentResult;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;

public class PaymentProcessor {

	private final Map<PaymentType, PaymentMethod> paymentMethodsByType;
	private final PaymentMethodValidator paymentMethodValidator;

	public PaymentProcessor(List<PaymentMethod> paymentMethods, PaymentMethodValidator paymentMethodValidator) {
		this.paymentMethodsByType = new EnumMap<>(PaymentType.class);
		for (PaymentMethod paymentMethod : paymentMethods) {
			this.paymentMethodsByType.put(paymentMethod.getPaymentType(), paymentMethod);
		}
		this.paymentMethodValidator = paymentMethodValidator;
	}

	public CompositePaymentResult process(List<PaymentCommand> paymentCommands, long totalAmount) {
		paymentMethodValidator.validate(paymentCommands, totalAmount);

		List<PaymentCommand> orderedPaymentCommands = orderByChannelPriority(paymentCommands);

		List<PaymentResult> succeededPaymentResults = new ArrayList<>();

		for (PaymentCommand paymentCommand : orderedPaymentCommands) {
			PaymentMethod paymentMethod = resolvePaymentMethod(paymentCommand.paymentType());
			PaymentResult paymentResult = paymentMethod.charge(paymentCommand);

			if (paymentResult.isFailure()) {
				boolean compensationCompleted = compensateInReverseOrder(succeededPaymentResults);
				return CompositePaymentResult.failure(paymentCommand.paymentType(), paymentResult.failureReason(),
					compensationCompleted);
			}
			succeededPaymentResults.add(paymentResult);
		}

		return CompositePaymentResult.success(succeededPaymentResults);
	}

	private List<PaymentCommand> orderByChannelPriority(List<PaymentCommand> paymentCommands) {
		List<PaymentCommand> ordered = new ArrayList<>(paymentCommands);
		ordered.sort(
			Comparator.comparingInt(paymentCommand -> paymentCommand.paymentType().isExternalChannel() ? 1 : 0));
		return ordered;
	}

	private boolean compensateInReverseOrder(List<PaymentResult> succeededPaymentResults) {
		List<PaymentResult> reversedResults = new ArrayList<>(succeededPaymentResults);
		Collections.reverse(reversedResults);

		boolean allCompensated = true;
		for (PaymentResult succeededResult : reversedResults) {
			try {
				PaymentMethod paymentMethod = resolvePaymentMethod(succeededResult.paymentType());
				PaymentResult refundResult = paymentMethod.refund(succeededResult.transactionId(),
					succeededResult.amount());
				if (refundResult.isFailure()) {
					allCompensated = false;
				}
			} catch (Exception exception) {
				allCompensated = false;
			}
		}
		return allCompensated;
	}

	private PaymentMethod resolvePaymentMethod(PaymentType paymentType) {
		PaymentMethod paymentMethod = paymentMethodsByType.get(paymentType);
		if (paymentMethod == null) {
			throw new IllegalStateException("지원하지 않는 결제 수단입니다. paymentType=" + paymentType);
		}
		return paymentMethod;
	}
}

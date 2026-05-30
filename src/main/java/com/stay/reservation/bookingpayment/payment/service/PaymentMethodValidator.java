package com.stay.reservation.bookingpayment.payment.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.stay.reservation.bookingpayment.payment.exception.InvalidPaymentCombinationException;
import com.stay.reservation.bookingpayment.payment.exception.PaymentAmountMismatchException;
import com.stay.reservation.bookingpayment.payment.model.PaymentCommand;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;

public class PaymentMethodValidator {

	private static final int MAX_PAYMENT_METHOD_COUNT = 2;

	public void validate(List<PaymentCommand> paymentCommands, long totalAmount) {
		validateNotEmpty(paymentCommands);
		validateCount(paymentCommands);
		validateNoDuplicateType(paymentCommands);
		validateExternalMethodsNotMixed(paymentCommands);
		validateAmountSum(paymentCommands, totalAmount);
	}

	private void validateNotEmpty(List<PaymentCommand> paymentCommands) {
		if (paymentCommands == null || paymentCommands.isEmpty()) {
			throw new InvalidPaymentCombinationException("결제 수단이 최소 1개 이상 필요합니다.");
		}
	}

	private void validateCount(List<PaymentCommand> paymentCommands) {
		if (paymentCommands.size() > MAX_PAYMENT_METHOD_COUNT) {
			throw new InvalidPaymentCombinationException(
				"결제 수단은 최대 " + MAX_PAYMENT_METHOD_COUNT + "개까지만 사용할 수 있습니다. " + "요청 수단 수=" + paymentCommands.size());
		}
	}

	private void validateNoDuplicateType(List<PaymentCommand> paymentCommands) {
		Set<PaymentType> seenPaymentTypes = EnumSet.noneOf(PaymentType.class);
		for (PaymentCommand paymentCommand : paymentCommands) {
			boolean added = seenPaymentTypes.add(paymentCommand.paymentType());
			if (!added) {
				throw new InvalidPaymentCombinationException(
					"동일한 결제 수단을 중복으로 사용할 수 없습니다. 중복 수단=" + paymentCommand.paymentType());
			}
		}
	}

	private void validateExternalMethodsNotMixed(List<PaymentCommand> paymentCommands) {
		long externalMethodCount = paymentCommands.stream()
			.filter(paymentCommand -> paymentCommand.paymentType().isExternalChannel())
			.count();
		if (externalMethodCount > 1) {
			throw new InvalidPaymentCombinationException("외부 결제 수단(신용카드, Y페이)은 서로 혼용할 수 없습니다.");
		}
	}

	private void validateAmountSum(List<PaymentCommand> paymentCommands, long totalAmount) {
		long sumOfAmounts = paymentCommands.stream().mapToLong(PaymentCommand::amount).sum();
		if (sumOfAmounts != totalAmount) {
			throw new PaymentAmountMismatchException(
				"결제 수단별 금액의 합이 총 결제 금액과 일치하지 않습니다. " + "합계=" + sumOfAmounts + ", 총액=" + totalAmount);
		}
	}
}

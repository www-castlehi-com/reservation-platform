package com.stay.reservation.bookingpayment.payment.model;

import java.util.Collections;
import java.util.List;

/**
 * 복합 결제 전체의 결과.
 *
 * <p>단일 결제든 복합 결제든 이 타입으로 결과가 반환된다.
 * 성공 시 각 수단의 {@link PaymentResult}를 모두 담고,
 * 실패 시 어떤 수단에서 실패했는지({@code failedPaymentType})를 담는다.
 *
 * @param success               전체 결제 성공 여부.
 * @param paymentResults        각 결제 수단의 결과 목록(성공 시).
 * @param failedPaymentType     실패한 결제 수단(실패 시). 성공 시 null.
 * @param failureReason         실패 사유(실패 시). 성공 시 null.
 * @param compensationCompleted 보상(환불) 완료 여부. 실패 시에만 의미 있음.
 */
public record CompositePaymentResult(boolean success, List<PaymentResult> paymentResults, PaymentType failedPaymentType,
									 String failureReason, boolean compensationCompleted) {

	public CompositePaymentResult {
		paymentResults = paymentResults == null ? Collections.emptyList() : List.copyOf(paymentResults);
	}

	/**
	 * 복합 결제 성공 결과.
	 */
	public static CompositePaymentResult success(List<PaymentResult> paymentResults) {
		return new CompositePaymentResult(true, paymentResults, null, null, true);
	}

	/**
	 * 복합 결제 실패 결과.
	 *
	 * @param failedPaymentType     실패한 수단.
	 * @param failureReason         실패 사유.
	 * @param compensationCompleted 성공했던 수단들의 보상(환불)이 모두 완료됐는지 여부.
	 *                              false라면 일부 환불이 실패한 것이므로 운영 개입이 필요하다.
	 */
	public static CompositePaymentResult failure(PaymentType failedPaymentType, String failureReason,
		boolean compensationCompleted) {
		return new CompositePaymentResult(false, Collections.emptyList(), failedPaymentType, failureReason,
			compensationCompleted);
	}
}

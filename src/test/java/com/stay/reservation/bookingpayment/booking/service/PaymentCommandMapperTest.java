package com.stay.reservation.bookingpayment.booking.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.payment.model.PaymentCommand;
import com.stay.reservation.bookingpayment.payment.model.PaymentDetail;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;

class PaymentCommandMapperTest {

	private final PaymentCommandMapper mapper = new PaymentCommandMapper();

	@Test
	@DisplayName("복합 결제 수단이 포함된 DTO를 도메인 PaymentCommand 리스트로 정확히 변환한다")
	void convertCompoundPaymentDtoToPaymentCommands() {
		// given
		BookingRequest.Payment payment = new BookingRequest.Payment(100000L,
			List.of(new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 80000L, "tok_card_test_123", null),
				new BookingRequest.Payment.Method(PaymentType.Y_POINT, 20000L, null, null)));

		// when
		List<PaymentCommand> commands = mapper.toCommands(payment, 1001L, "key-idempotency-001");

		// then
		assertThat(commands).hasSize(2);

		PaymentCommand cardCommand = commands.get(0);
		assertThat(cardCommand.paymentType()).isEqualTo(PaymentType.CREDIT_CARD);
		assertThat(cardCommand.amount()).isEqualTo(80000L);
		assertThat(cardCommand.userId()).isEqualTo(1001L);
		assertThat(cardCommand.idempotencyKey()).isEqualTo("key-idempotency-001");
		assertThat(cardCommand.paymentDetail()).isInstanceOf(PaymentDetail.CardPaymentDetail.class);
		assertThat(((PaymentDetail.CardPaymentDetail) cardCommand.paymentDetail()).cardToken()).isEqualTo(
			"tok_card_test_123");

		PaymentCommand pointCommand = commands.get(1);
		assertThat(pointCommand.paymentType()).isEqualTo(PaymentType.Y_POINT);
		assertThat(pointCommand.amount()).isEqualTo(20000L);
		assertThat(pointCommand.paymentDetail()).isInstanceOf(PaymentDetail.PointPaymentDetail.class);
	}

	@Test
	@DisplayName("신용카드 결제 수단에서 카드 토큰 정보가 누락된 경우 예외가 발생한다")
	void creditCardPaymentWithoutTokenThrowsException() {
		// given
		BookingRequest.Payment payment = new BookingRequest.Payment(50000L,
			List.of(new BookingRequest.Payment.Method(PaymentType.CREDIT_CARD, 50000L, null, null) // cardToken 누락
			));

		// when & then
		assertThatThrownBy(() -> mapper.toCommands(payment, 1001L, "key-idempotency-002")).isInstanceOf(
			IllegalArgumentException.class).hasMessageContaining("cardToken은 필수입니다.");
	}
}

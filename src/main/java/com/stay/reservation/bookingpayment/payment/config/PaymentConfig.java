package com.stay.reservation.bookingpayment.payment.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stay.reservation.bookingpayment.payment.infra.MockPgClient;
import com.stay.reservation.bookingpayment.payment.port.PointBalancePort;
import com.stay.reservation.bookingpayment.payment.port.pg.PgClient;
import com.stay.reservation.bookingpayment.payment.service.PaymentMethod;
import com.stay.reservation.bookingpayment.payment.service.PaymentMethodValidator;
import com.stay.reservation.bookingpayment.payment.service.PaymentProcessor;

@Configuration
public class PaymentConfig {

	@Bean
	public PaymentMethodValidator paymentMethodValidator() {
		return new PaymentMethodValidator();
	}

	@Bean
	public PaymentProcessor paymentProcessor(List<PaymentMethod> paymentMethods,
		PaymentMethodValidator paymentMethodValidator) {
		return new PaymentProcessor(paymentMethods, paymentMethodValidator);
	}

	@Bean
	public PgClient cardPgClient() {
		return new MockPgClient("CARD");
	}

	@Bean
	public PgClient yPayPgClient() {
		return new MockPgClient("YPAY");
	}

	@Bean
	public PointBalancePort pointBalancePort() {
		return new PointBalancePort() {
			@Override
			public String deduct(long userId, long amount, String idempotencyKey) {
				throw new UnsupportedOperationException("PointBalancePort 미구현 (Day 2/3)");
			}

			@Override
			public String restore(String pointTransactionId, long amount) {
				throw new UnsupportedOperationException("PointBalancePort 미구현 (Day 2/3)");
			}
		};
	}
}

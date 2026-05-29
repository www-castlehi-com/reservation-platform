package com.stay.reservation.bookingpayment.payment.port.pg;

public record PgAuthorizeRequest(String idempotencyKey, String paymentToken, long amount) {

}

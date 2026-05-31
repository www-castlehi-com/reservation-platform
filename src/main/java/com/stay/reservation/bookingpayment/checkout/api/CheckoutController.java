package com.stay.reservation.bookingpayment.checkout.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stay.reservation.bookingpayment.checkout.dto.CheckoutResponse;
import com.stay.reservation.bookingpayment.checkout.service.CheckoutService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CheckoutController {

	private final CheckoutService checkoutService;

	@GetMapping("/checkout")
	public CheckoutResponse getCheckout(@RequestParam Long productId, @RequestHeader("X-User-Id") Long userId) {
		return checkoutService.getCheckout(productId, userId);
	}
}

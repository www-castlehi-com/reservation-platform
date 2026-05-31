package com.stay.reservation.bookingpayment.checkout.dto;

import com.stay.reservation.bookingpayment.user.domain.UserWallet;

public record UserWalletInfo(Long userId, Long availablePoints) {

	public static UserWalletInfo from(UserWallet wallet) {
		return new UserWalletInfo(wallet.getUserId(), wallet.getPointBalance());
	}
}

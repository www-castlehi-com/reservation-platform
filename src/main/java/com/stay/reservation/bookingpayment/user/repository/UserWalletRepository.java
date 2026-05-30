package com.stay.reservation.bookingpayment.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stay.reservation.bookingpayment.user.domain.UserWallet;

@Repository
public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {

}

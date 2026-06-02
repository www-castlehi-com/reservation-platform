package com.stay.reservation.bookingpayment.payment.infra;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.common.exception.UserNotFoundException;
import com.stay.reservation.bookingpayment.payment.exception.InsufficientPointException;
import com.stay.reservation.bookingpayment.payment.port.PointBalancePort;
import com.stay.reservation.bookingpayment.point.domain.PointTransaction;
import com.stay.reservation.bookingpayment.point.domain.PointTransactionType;
import com.stay.reservation.bookingpayment.point.repository.PointTransactionRepository;
import com.stay.reservation.bookingpayment.user.domain.UserWallet;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PointBalanceAdapter implements PointBalancePort {

	private final UserWalletRepository userWalletRepository;
	private final PointTransactionRepository pointTransactionRepository;

	@Override
	@Transactional
	public String deduct(long userId, long amount, String idempotencyKey) {
		log.info("Point deduct: userId={}, amount={}, idempotencyKey={}", userId, amount, idempotencyKey);

		UserWallet wallet = userWalletRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

		if (wallet.getPointBalance() < amount) {
			throw new InsufficientPointException(userId, wallet.getPointBalance(), amount);
		}

		long balanceBefore = wallet.getPointBalance();
		wallet.deductPoint(amount);
		userWalletRepository.save(wallet);

		PointTransaction pointTransaction = PointTransaction.builder()
			.userId(userId)
			.amount(amount)
			.type(PointTransactionType.USE)
			.balanceBefore(balanceBefore)
			.balanceAfter(wallet.getPointBalance())
			.idempotencyKey(idempotencyKey)
			.build();

		PointTransaction saved = pointTransactionRepository.save(pointTransaction);
		log.info("Point deducted. txId={}, balanceAfter={}", saved.getId(), wallet.getPointBalance());

		return String.valueOf(saved.getId());
	}

	@Override
	@Transactional
	public String restore(String pointTransactionId, long amount) {
		Long originalTransactionId = Long.parseLong(pointTransactionId);
		log.info("Point restore: originalTransactionId={}, amount={}", originalTransactionId, amount);

		boolean alreadyRestored = pointTransactionRepository.existsByOriginalTransactionIdAndType(originalTransactionId,
			PointTransactionType.RESTORE);
		if (alreadyRestored) {
			log.info("Already restored. Skipping. originalTransactionId={}", originalTransactionId);
			return pointTransactionId;
		}

		PointTransaction originalTransaction = pointTransactionRepository.findById(originalTransactionId)
			.orElseThrow(() -> new IllegalStateException("Original tx not found: " + originalTransactionId));

		UserWallet wallet = userWalletRepository.findById(originalTransaction.getUserId())
			.orElseThrow(() -> new UserNotFoundException(originalTransaction.getUserId()));

		long balanceBefore = wallet.getPointBalance();
		wallet.addPoint(amount);
		userWalletRepository.save(wallet);

		PointTransaction restoreTransaction = PointTransaction.builder()
			.userId(originalTransaction.getUserId())
			.amount(amount)
			.type(PointTransactionType.RESTORE)
			.balanceBefore(balanceBefore)
			.balanceAfter(wallet.getPointBalance())
			.originalTransactionId(originalTransactionId)
			.idempotencyKey(originalTransaction.getIdempotencyKey())
			.build();

		PointTransaction saved = pointTransactionRepository.save(restoreTransaction);
		log.info("Point restored. restoreTxId={}, balanceAfter={}", saved.getId(), wallet.getPointBalance());

		return String.valueOf(saved.getId());
	}
}

package com.stay.reservation.bookingpayment.payment.infra;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.payment.exception.InsufficientPointException;
import com.stay.reservation.bookingpayment.payment.port.PointBalancePort;
import com.stay.reservation.bookingpayment.point.domain.PointTransaction;
import com.stay.reservation.bookingpayment.point.domain.PointTransactionType;
import com.stay.reservation.bookingpayment.point.repository.PointTransactionRepository;
import com.stay.reservation.bookingpayment.user.domain.UserWallet;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

@SpringBootTest
@Transactional
class PointBalanceAdapterTest {

	@Autowired
	private PointBalancePort port;

	@Autowired
	private UserWalletRepository walletRepository;

	@Autowired
	private PointTransactionRepository txRepository;

	@Test
	@DisplayName("포인트 차감 성공 - 지갑 잔액이 정상 삭감되고 USE 거래 로그가 생성된다")
	void deductPointsSuccessfully() {
		// given: user 1001 (시드 데이터 유저, pointBalance = 50000)
		// when
		String txId = port.deduct(1001L, 10000L, "test-key-001");

		// then
		UserWallet wallet = walletRepository.findById(1001L).orElseThrow();
		assertThat(wallet.getPointBalance()).isEqualTo(40000L);

		PointTransaction tx = txRepository.findById(Long.parseLong(txId)).orElseThrow();
		assertThat(tx.getType()).isEqualTo(PointTransactionType.USE);
		assertThat(tx.getBalanceBefore()).isEqualTo(50000L);
		assertThat(tx.getBalanceAfter()).isEqualTo(40000L);
	}

	@Test
	@DisplayName("포인트 환불 성공 - 차감되었던 포인트가 복구되고 RESTORE 거래 로그가 생성된다")
	void restorePointsSuccessfully() {
		// given: 차감 선행 작업
		String useTxId = port.deduct(1002L, 10000L, "test-key-002");

		// when
		port.restore(useTxId, 10000L);

		// then
		UserWallet wallet = walletRepository.findById(1002L).orElseThrow();
		assertThat(wallet.getPointBalance()).isEqualTo(50000L);  // 원복
	}

	@Test
	@DisplayName("포인트 차감 예외 - 잔액보다 초과된 차감 요청 발생 시 InsufficientPointException이 전파된다")
	void deductPointsInsufficientBalanceThrowsException() {
		assertThatThrownBy(() -> port.deduct(1003L, 999999L, "test-key-003")).isInstanceOf(
			InsufficientPointException.class);
	}

	@Test
	@DisplayName("환불 멱등성 보장 - 동일 차감 건에 대해 중복 환불 요청 시 최초 1회만 반영되고 이후 스킵된다")
	void restorePointsIdempotencyAssured() {
		// given
		String useTxId = port.deduct(1004L, 10000L, "test-key-004");

		// when: 같은 트랜잭션 ID에 대해 두 번의 복구 수행
		port.restore(useTxId, 10000L);
		port.restore(useTxId, 10000L);  // 두 번째 복구는 멱등성 필터에 의해 스킵되어야 함

		// then: 잔액은 중복 지급 없이 원래 금액인 50,000원으로 단 한 번만 복구 유지됨
		UserWallet wallet = walletRepository.findById(1004L).orElseThrow();
		assertThat(wallet.getPointBalance()).isEqualTo(50000L);
	}
}

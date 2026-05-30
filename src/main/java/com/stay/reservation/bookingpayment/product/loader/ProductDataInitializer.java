package com.stay.reservation.bookingpayment.product.loader;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.product.repository.ProductRepository;
import com.stay.reservation.bookingpayment.roomtype.domain.RoomType;
import com.stay.reservation.bookingpayment.roomtype.repository.RoomTypeRepository;
import com.stay.reservation.bookingpayment.user.domain.UserWallet;
import com.stay.reservation.bookingpayment.user.repository.UserWalletRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductDataInitializer implements CommandLineRunner {

	private final RoomTypeRepository roomTypeRepository;
	private final ProductRepository productRepository;
	private final UserWalletRepository userWalletRepository;

	@Override
	@Transactional
	public void run(String... args) {
		log.info("Starting data initialization: RoomType → Product → UserWallet...");

		seedRoomTypesAndProducts();
		seedUserWallets();

		log.info("Data initialization complete.");
	}

	private void seedRoomTypesAndProducts() {
		// ── Step 1. RoomType 저장 ────────────────────────────────────────────────
		RoomType deluxeDouble = roomTypeRepository.save(RoomType.builder()
			.title("신라호텔 서울 디럭스 더블룸")
			.originalPrice(250000L)
			.checkInTime(LocalTime.of(15, 0))
			.checkOutTime(LocalTime.of(11, 0))
			.build());

		RoomType suiteRoom = roomTypeRepository.save(RoomType.builder()
			.title("신라호텔 서울 프리미어 스위트룸")
			.originalPrice(600000L)
			.checkInTime(LocalTime.of(15, 0))
			.checkOutTime(LocalTime.of(12, 0))
			.build());

		RoomType poolVilla = roomTypeRepository.save(RoomType.builder()
			.title("제주 감성 독채 풀빌라")
			.originalPrice(500000L)
			.checkInTime(LocalTime.of(16, 0))
			.checkOutTime(LocalTime.of(11, 0))
			.build());

		log.info("Step 1 — RoomType 저장 완료: deluxeId={}, suiteId={}, poolVillaId={}", deluxeDouble.getId(),
			suiteRoom.getId(), poolVilla.getId());

		// ── Step 2. Product 저장 ─────────────
		List<Product> products = new ArrayList<>();
		LocalDate baseDate = LocalDate.of(2026, 12, 24);
		LocalDateTime openNow = LocalDateTime.now().minusHours(1);

		// A. 디럭스룸 4개 상품 등록 (12/24 ~ 12/27 투숙)
		for (int i = 0; i < 4; i++) {
			products.add(Product.builder()
				.roomTypeId(deluxeDouble.getId())
				.stayDate(baseDate.plusDays(i))
				.price(89000L) // 선착순 초특가 8.9만
				.totalStock(10)
				.openAt(openNow)
				.build());
		}

		// B. 스위트룸 3개 상품 등록 (12/24 ~ 12/26 투숙)
		for (int i = 0; i < 3; i++) {
			products.add(Product.builder()
				.roomTypeId(suiteRoom.getId())
				.stayDate(baseDate.plusDays(i))
				.price(159000L) // 스위트 초특가 15.9만
				.totalStock(10)
				.openAt(openNow)
				.build());
		}

		// C. 풀빌라 3개 상품 등록 (12/24 ~ 12/26 투숙)
		for (int i = 0; i < 3; i++) {
			products.add(Product.builder()
				.roomTypeId(poolVilla.getId())
				.stayDate(baseDate.plusDays(i))
				.price(129000L) // 풀빌라 초특가 12.9만
				.totalStock(10)
				.openAt(openNow)
				.build());
		}

		productRepository.saveAll(products);
		log.info("Step 2 — Product 저장 완료: 총 10개 상품 등록 완료 (각 재고 10개)");
	}

	private void seedUserWallets() {
		userWalletRepository.saveAll(List.of(UserWallet.builder().userId(1001L).pointBalance(50000L).build(),
			UserWallet.builder().userId(1002L).pointBalance(50000L).build(),
			UserWallet.builder().userId(1003L).pointBalance(50000L).build(),
			UserWallet.builder().userId(1004L).pointBalance(50000L).build(),
			UserWallet.builder().userId(1005L).pointBalance(50000L).build()));
		log.info("Step 3 — UserWallet 시드 완료: userId 1001~1005, pointBalance=50000 each");
	}
}

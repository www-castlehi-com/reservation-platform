package com.stay.reservation.bookingpayment.product.loader;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
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
	private final StringRedisTemplate redisTemplate;

	@Override
	@Transactional
	public void run(String... args) {
		log.info("Starting data initialization: RoomType → Product → UserWallet...");

		if (productRepository.count() > 0) {
			log.info("Database is already seeded. Skipping DB initialization.");

			if (userWalletRepository.count() < 2000) {
				log.info("User wallets are insufficient ({} < 2000). Re-seeding user wallets...",
					userWalletRepository.count());
				userWalletRepository.deleteAll();
				seedUserWallets();
			}

			log.info("Starting Redis stock cache warm-up for existing products...");
			List<Product> products = productRepository.findAll();
			for (Product product : products) {
				String key = "stock:product:" + product.getId();
				redisTemplate.opsForValue().set(key, String.valueOf(product.getTotalStock()));
			}
			log.info("Redis stock cache warm-up complete for {} existing products.", products.size());
			return;
		}

		seedRoomTypesAndProducts();
		seedUserWallets();

		log.info("Data initialization complete.");
	}

	private void seedRoomTypesAndProducts() {
		// ── Step 1. RoomType 저장 ────────────────────────────────────────────────
		RoomType suiteRoom = roomTypeRepository.save(RoomType.builder()
			.title("신라호텔 서울 프리미어 스위트룸")
			.originalPrice(600000L)
			.checkInTime(LocalTime.of(15, 0))
			.checkOutTime(LocalTime.of(12, 0))
			.build());

		log.info("Step 1 — RoomType 저장 완료: suiteId={}", suiteRoom.getId());

		// ── Step 2. Product 저장 (날짜별 SKU, 총 10개, 각 재고 10개 한정) ─────────────
		List<Product> products = new ArrayList<>();
		LocalDate baseDate = LocalDate.of(2026, 12, 24); // 기준일 (크리스마스 이브)
		LocalDateTime openNow = LocalDateTime.now().minusHours(1); // 즉시 예약 가능하도록 세팅

		// 스위트룸 3개 상품 등록 (12/24 ~ 12/26 투숙)
		products.add(Product.builder().roomTypeId(suiteRoom.getId()).stayDate(baseDate).price(159000L) // 스위트 초특가 15.9만
			.totalStock(10).openAt(openNow).build());

		List<Product> savedProducts = productRepository.saveAll(products);
		log.info("Step 2 — Product 저장 완료: 총 10개 상품 등록 완료");

		// ── Step 2-1. Redis 실시간 재고(stock:product:{id}) 웜업 ──────────────────
		log.info("Step 2-1 — Redis 실시간 재고 키 웜업 시작...");
		for (Product savedProduct : savedProducts) {
			String key = "stock:product:" + savedProduct.getId();
			redisTemplate.opsForValue().set(key, String.valueOf(savedProduct.getTotalStock()));
		}
		log.info("Step 2-1 — Redis 실시간 재고 웜업 완료: 10개 상품 재고 각 10개로 웜업 완료");
	}

	private void seedUserWallets() {
		List<UserWallet> wallets = new ArrayList<>();
		for (long i = 1001; i <= 3000; i++) {
			wallets.add(UserWallet.builder().userId(i).pointBalance(50000L).build());
		}
		userWalletRepository.saveAll(wallets);
		log.info("Step 3 — UserWallet 시드 완료: userId 1001~3000, pointBalance=50000 each");
	}
}

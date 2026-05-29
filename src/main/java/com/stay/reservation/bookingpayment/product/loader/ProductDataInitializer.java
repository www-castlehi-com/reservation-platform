package com.stay.reservation.bookingpayment.product.loader;

import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductDataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            log.info("Starting Product database initialization with 10 realistic products...");

            List<Product> products = new ArrayList<>();
            LocalDate baseDate = LocalDate.now().plusDays(7);

            // 1. 서울 호캉스 호텔 상품
            products.add(Product.builder()
                    .title("서울 디럭스 호캉스 더블룸 (웰컴 미니바 패키지)")
                    .originalPrice(350000L)
                    .discountPrice(280000L)
                    .totalStock(5)
                    .checkInTime(baseDate.atTime(15, 0))
                    .checkOutTime(baseDate.plusDays(1).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(1)) // 어제 오픈되어 현재 예약 가능
                    .build());

            // 2. 제주 감성 독채 펜션
            products.add(Product.builder()
                    .title("제주 감성 독채 감귤밭 스테이")
                    .originalPrice(280000L)
                    .discountPrice(210000L)
                    .totalStock(2)
                    .checkInTime(baseDate.plusDays(1).atTime(16, 0))
                    .checkOutTime(baseDate.plusDays(2).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(2))
                    .build());

            // 3. 부산 프리미엄 오션뷰 호텔
            products.add(Product.builder()
                    .title("부산 프리미엄 오션뷰 오션 테라스 더블룸")
                    .originalPrice(450000L)
                    .discountPrice(380000L)
                    .totalStock(8)
                    .checkInTime(baseDate.plusDays(2).atTime(15, 0))
                    .checkOutTime(baseDate.plusDays(3).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(1))
                    .build());

            // 4. 가평 프라이빗 풀빌라
            products.add(Product.builder()
                    .title("가평 프라이빗 온수풀 빌라 (바비큐 그릴 패키지 포함)")
                    .originalPrice(600000L)
                    .discountPrice(480000L)
                    .totalStock(3)
                    .checkInTime(baseDate.plusDays(3).atTime(15, 0))
                    .checkOutTime(baseDate.plusDays(4).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(3))
                    .build());

            // 5. 강릉 해돋이 뷰 호텔
            products.add(Product.builder()
                    .title("강릉 정동진 해돋이 뷰 슈페리어 패밀리룸")
                    .originalPrice(220000L)
                    .discountPrice(180000L)
                    .totalStock(15)
                    .checkInTime(baseDate.plusDays(4).atTime(15, 0))
                    .checkOutTime(baseDate.plusDays(5).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(1))
                    .build());

            // 6. 경주 전통 한옥 스테이
            products.add(Product.builder()
                    .title("경주 한옥 고택 스테이 - 전통 다도 세트 패키지")
                    .originalPrice(180000L)
                    .discountPrice(150000L)
                    .totalStock(4)
                    .checkInTime(baseDate.plusDays(5).atTime(15, 0))
                    .checkOutTime(baseDate.plusDays(6).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(5))
                    .build());

            // 7. 여수 야경 스위트 콘도
            products.add(Product.builder()
                    .title("여수 돌산대교 야경뷰 스위트 콘도미니엄")
                    .originalPrice(320000L)
                    .discountPrice(260000L)
                    .totalStock(10)
                    .checkInTime(baseDate.plusDays(6).atTime(15, 0))
                    .checkOutTime(baseDate.plusDays(7).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(1))
                    .build());

            // 8. 인천 네스트 마운틴뷰 호텔
            products.add(Product.builder()
                    .title("인천 영종도 네스트 감성 마운틴뷰 트윈룸")
                    .originalPrice(250000L)
                    .discountPrice(210000L)
                    .totalStock(12)
                    .checkInTime(baseDate.plusDays(7).atTime(15, 0))
                    .checkOutTime(baseDate.plusDays(8).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(1))
                    .build());

            // 9. 남해 아난티 최고급 펜트하우스
            products.add(Product.builder()
                    .title("남해 아난티 펜트하우스 프라이빗 패키지 (4인 조식포함)")
                    .originalPrice(850000L)
                    .discountPrice(720000L)
                    .totalStock(1)
                    .checkInTime(baseDate.plusDays(8).atTime(15, 0))
                    .checkOutTime(baseDate.plusDays(9).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(4))
                    .build());

            // 10. 속초 울산바위 뷰 디럭스룸
            products.add(Product.builder()
                    .title("속초 설악산 울산바위 뷰 패밀리 디럭스룸")
                    .originalPrice(300000L)
                    .discountPrice(240000L)
                    .totalStock(7)
                    .checkInTime(baseDate.plusDays(9).atTime(15, 0))
                    .checkOutTime(baseDate.plusDays(10).atTime(11, 0))
                    .openAt(LocalDateTime.now().minusDays(1))
                    .build());

            productRepository.saveAll(products);
            log.info("Successfully initialized 10 realistic products in the database.");
        } else {
            log.info("Products already exist in the database. Skipping initialization.");
        }
    }
}

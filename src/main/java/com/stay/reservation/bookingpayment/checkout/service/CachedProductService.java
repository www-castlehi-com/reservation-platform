package com.stay.reservation.bookingpayment.checkout.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stay.reservation.bookingpayment.checkout.cache.CachedProductInfoCacheRepository;
import com.stay.reservation.bookingpayment.checkout.dto.CachedProductInfo;
import com.stay.reservation.bookingpayment.common.exception.ProductNotFoundException;
import com.stay.reservation.bookingpayment.product.domain.Product;
import com.stay.reservation.bookingpayment.product.repository.ProductRepository;
import com.stay.reservation.bookingpayment.roomtype.domain.RoomType;
import com.stay.reservation.bookingpayment.roomtype.repository.RoomTypeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedProductService {

	private final CachedProductInfoCacheRepository cachedProductInfoCacheRepository;
	private final ProductRepository productRepository;
	private final RoomTypeRepository roomTypeRepository;

	@Transactional(readOnly = true)
	public CachedProductInfo getProductInfo(Long productId) {
		CachedProductInfo cached = cachedProductInfoCacheRepository.find(productId);
		if (cached != null) {
			log.debug("ProductInfo cache hit for productId: {}", productId);
			return cached;
		}

		log.debug("ProductInfo cache miss for productId: {}", productId);
		return loadAndCacheProductInfo(productId);
	}

	private CachedProductInfo loadAndCacheProductInfo(Long productId) {
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new ProductNotFoundException(productId));

		RoomType roomType = roomTypeRepository.findById(product.getRoomTypeId())
			.orElseThrow(() -> new IllegalStateException(
				"RoomType not found: roomTypeId=" + product.getRoomTypeId() + ", productId=" + productId));

		CachedProductInfo info = CachedProductInfo.from(product, roomType);
		cachedProductInfoCacheRepository.save(productId, info);
		return info;
	}
}

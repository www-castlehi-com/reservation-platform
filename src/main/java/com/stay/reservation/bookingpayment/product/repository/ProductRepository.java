package com.stay.reservation.bookingpayment.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stay.reservation.bookingpayment.product.domain.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

}

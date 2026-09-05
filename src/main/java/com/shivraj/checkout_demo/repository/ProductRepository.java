package com.shivraj.checkout_demo.repository;

import com.shivraj.checkout_demo.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product,Long> {
}

package com.shivraj.checkout_demo.repository;

import com.shivraj.checkout_demo.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order,Long> {
}

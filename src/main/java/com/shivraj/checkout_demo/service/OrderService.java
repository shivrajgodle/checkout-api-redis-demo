package com.shivraj.checkout_demo.service;

import com.shivraj.checkout_demo.entity.Order;
import com.shivraj.checkout_demo.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public Order saveOrder(Long productId, Long customerId, int quantity, BigDecimal totalPrice, String status){
        Order order = new Order();
        order.setProductId(productId);
        order.setCustomerId(customerId);
        order.setQuantity(quantity);
        order.setTotalPrice(totalPrice);
        order.setStatus(status);
        order.setCreatedAt(Instant.now());
        return orderRepository.save(order);
    }

}

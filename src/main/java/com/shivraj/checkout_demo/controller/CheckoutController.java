package com.shivraj.checkout_demo.controller;

import com.shivraj.checkout_demo.entity.Order;
import com.shivraj.checkout_demo.service.CheckoutOrchestratorService;
import com.shivraj.checkout_demo.service.InventoryReservationService;
import com.shivraj.checkout_demo.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutOrchestratorService orchestratorService;
    private final InventoryReservationService inventoryReservationService;
    private final OrderService orderService;


    public record CheckoutRequest(Long productId, Long customerId, Integer quantity){}

    @PostMapping
    public ResponseEntity<?> checkout(@RequestBody CheckoutRequest request){

        // 1. Gather product, inventory, customer concurrently (CompletableFuture)
        CheckoutOrchestratorService.CheckoutData data = orchestratorService.getCheckoutData(request.productId(), request.customerId());

        // 2. Atomically reserve stock (ConcurrentHashMap)
        boolean reserved = inventoryReservationService.reserve(request.productId(), request.quantity());
        if(!reserved){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Insufficient stock for product "+request.productId);
        }

        try{
            // 3. Persist the order (PostgreSQL)
            BigDecimal totalPrice = data.product().getPrice()
                    .multiply(new BigDecimal(request.quantity()));

            Order order = orderService.saveOrder(request.productId(),request.customerId(),request.quantity(),totalPrice,"CONFIRMED");
            return ResponseEntity.ok(order);

        } catch (Exception ex){
            // 4. Roll back the in-memory reservation if persistence fails
            inventoryReservationService.release(request.productId(),request.quantity());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Checkout Failed: "+ex.getMessage());
        }
    }


    @GetMapping("/reservations/{productId}")
    public ResponseEntity<Integer> getInFlightReservation(@PathVariable Long productId){
        return ResponseEntity.ok(inventoryReservationService.getInFlightReservations(productId));
    }

}

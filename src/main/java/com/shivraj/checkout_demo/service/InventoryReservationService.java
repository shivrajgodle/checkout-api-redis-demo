package com.shivraj.checkout_demo.service;

import com.shivraj.checkout_demo.entity.Inventory;
import com.shivraj.checkout_demo.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    // productId -> quantity currently reserved in-flight (not yet committed to DB)
    private final ConcurrentHashMap<Long,Integer> reservations = new ConcurrentHashMap<>();

    private final InventoryRepository inventoryRepository;

    /**
     * Attempts to reserve `quantity` units of a product.
     * Returns true if the reservation succeeded, false if not enough stock is available
     * once in-flight reservations are accounted for.
     */
    public boolean reserve(Long productId, int quantity){
        Inventory inventory = inventoryRepository.findByProductId(productId).orElseThrow(()-> throw new IllegalArgumentException("No inventory for product: " + productId));

        int available = inventory.getAvailableQuantity();

        // compute() runs atomically per key: no other thread can interleave
        // a read-then-write on the SAME key while this lambda executes.
        final boolean[] success = {false};

        reservations.compute(productId, (id, currentlyReserved) -> {
            int reservedSoFor = (currentlyReserved == null) ? 0 : currentlyReserved;

            if(reservedSoFor + quantity > available){
                success[0] = false;
                return currentlyReserved; // no change
            }

            success[0] = true;
            return reservedSoFor + quantity;
        });

        return success[0];
    }


    /**
     * Releases a reservation, e.g. after the order commits (moving the hold into
     * a real DB decrement) or after checkout fails/times out.
     */
    public void release(Long productId, int quantity){
        reservations.computeIfPresent(productId,(id, currentlyReserved) -> {
            int updated = currentlyReserved - quantity;
            return (updated <= 0) ? null : updated; // remove the key entirely once it hits 0
        });
    }

    public int getInFlightReservations(Long productId){
        return reservations.getOrDefault(productId, 0);
    }

}

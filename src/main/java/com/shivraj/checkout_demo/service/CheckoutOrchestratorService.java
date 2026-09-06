package com.shivraj.checkout_demo.service;

import com.shivraj.checkout_demo.entity.Inventory;
import com.shivraj.checkout_demo.entity.Product;
import com.shivraj.checkout_demo.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
public class CheckoutOrchestratorService {

    private final PricingCacheService pricingCacheService;
    private final InventoryRepository inventoryRepository;

    @Qualifier("checkoutExecutor")
    private final Executor checkoutExecutor;

    public CheckoutData getCheckoutData(Long productId, Long customerId){
        CompletableFuture<Product> productFuture =
                CompletableFuture.supplyAsync(()-> pricingCacheService.getProduct(productId),checkoutExecutor);

        CompletableFuture<Inventory> inventoryFuture =
                CompletableFuture.supplyAsync(() -> inventoryRepository.findByProductId(productId)
                        .orElseThrow(()-> new IllegalArgumentException("No inventory for product:"+productId)),checkoutExecutor);

        CompletableFuture<CustomerInfo> customerFuture =
                CompletableFuture.supplyAsync(() -> fetchCustomerInfo(customerId) , checkoutExecutor)
                        .exceptionally(ex -> {
                            System.out.println("Customer lookup failed, falling back to guest info:"+ex.getMessage());
                            return CustomerInfo.guest(customerId);
                        });

        CompletableFuture<ProductInventory> productAndInventory = productFuture.thenCombine(inventoryFuture, ProductInventory::new);

        CompletableFuture<CheckoutData> checkoutDataFuture = productAndInventory.thenCombine(customerFuture, (pi, customer) -> new CheckoutData(pi.product(), pi.inventory(), customer));

        return checkoutDataFuture.join();
    }


    private CustomerInfo fetchCustomerInfo(Long customerId){
        try{
            // Simulates a slow external call, e.g. a Customer microservice over HTTP
            Thread.sleep(150);
        } catch (InterruptedException ex){
            Thread.currentThread().interrupt();
        }
        return new CustomerInfo(customerId,"Customer-"+customerId, "customer"+customerId+"@example.com");
    }

    private record ProductInventory(Product product,Inventory inventory){}

    public record CustomerInfo(Long customerId, String name, String email){
        public static CustomerInfo guest(Long customerId){
            return new CustomerInfo(customerId, "Guest", "unknown");
        }
    }

    public record CheckoutData(Product product, Inventory inventory, CustomerInfo customer){}
}

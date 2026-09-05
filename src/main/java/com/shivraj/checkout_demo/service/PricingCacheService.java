package com.shivraj.checkout_demo.service;

import com.shivraj.checkout_demo.entity.Product;
import com.shivraj.checkout_demo.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PricingCacheService {

    private static final String CACHE_PREFIX = "product:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Product> productRedisTemplate;
    private final ProductRepository productRepository;

    public Product getProduct(Long productId){
        String key = CACHE_PREFIX + productId;

        // 1. Cache check
        Product cached = productRedisTemplate.opsForValue().get(key);
        if(cached != null){
            System.out.println("Cache HIT for product " + productId);
            return cached;
        }

        // 2. Cache miss -> DB fallback
        System.out.println("Cache MISS for product " + productId + ", querying DB");
        Product product = productRepository.findById(productId).orElseThrow(()-> new IllegalArgumentException("Product not found: " + productId));

        // 3. Populate cache
        productRedisTemplate.opsForValue().set(key,product,TTL);

        return product;
    }

    public void evictProduct(Long productId){
        productRedisTemplate.delete(CACHE_PREFIX + productId);
    }

    public void updateProduct(Product product){
        productRepository.save(product);
        // Update-then-evict (rather than update-in-place) keeps this simple and avoids
        // serving stale cached data if the write path changes later.
        evictProduct(product.getId());
    }

}

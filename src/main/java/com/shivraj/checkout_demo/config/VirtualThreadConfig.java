package com.shivraj.checkout_demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class VirtualThreadConfig {

    @Bean(name = "checkoutExecutor")
    public Executor checkoutExecutor(){
        // One virtual thread per submitted task. Cheap to create, cheap to block —
        // unlike a fixed platform-thread pool, we don't need to size this based on CPU cores.
        return Executors.newVirtualThreadPerTaskExecutor();
    }

}

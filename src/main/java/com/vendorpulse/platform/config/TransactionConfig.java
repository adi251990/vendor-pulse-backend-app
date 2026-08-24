package com.vendorpulse.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class TransactionConfig {

    /**
     * Used by ClaimService to run the "re-check + insert booking" step
     * inside the Redisson lock without relying on Spring AOP self-invocation
     * (a plain internal @Transactional method call wouldn't go through the
     * proxy and the transaction boundary would silently not apply).
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}

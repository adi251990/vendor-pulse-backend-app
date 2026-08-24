package com.hireme.platform.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * redisson-spring-boot-starter would normally auto-configure this from
 * spring.data.redis.* properties, but we declare it explicitly so the lease/
 * retry behaviour used by ClaimService (see booking module) is easy to find
 * and tune in one place.
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(@Value("${spring.data.redis.host}") String host,
                                          @Value("${spring.data.redis.port}") int port) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(8);
        return Redisson.create(config);
    }
}

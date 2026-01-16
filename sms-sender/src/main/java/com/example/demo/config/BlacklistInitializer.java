package com.example.demo.config;

import com.example.demo.service.BlacklistCache;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlacklistInitializer {

    @Bean
    CommandLineRunner initBlacklist(BlacklistCache blacklistCache) {
        return args -> {
            // Pre-seed blocked test number
            blacklistCache.addToBlacklist("+1111111111");
            System.out.println("✓ Blacklist initialized - Blocked number: +1111111111");
        };
    }
}

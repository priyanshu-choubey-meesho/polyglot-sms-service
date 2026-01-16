package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class BlacklistCache {
    private final StringRedisTemplate redisTemplate;
    private static final String BLACKLIST_PREFIX = "blacklist:";

    @Autowired
    public BlacklistCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isBlacklisted(String phoneNumber) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + phoneNumber));
    }

    public void addToBlacklist(String phoneNumber) {
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + phoneNumber, "1");
    }

    public void removeFromBlacklist(String phoneNumber) {
        redisTemplate.delete(BLACKLIST_PREFIX + phoneNumber);
    }
}

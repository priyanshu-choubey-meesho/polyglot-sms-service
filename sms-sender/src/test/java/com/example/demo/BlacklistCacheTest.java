package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import com.example.demo.service.BlacklistCache;

@ExtendWith(MockitoExtension.class)
public class BlacklistCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private BlacklistCache blacklistCache;

    @BeforeEach
    public void setUp() {
        blacklistCache = new BlacklistCache(redisTemplate);
    }

    @Test
    public void testIsBlacklistedReturnsTrueWhenPhoneNumberExists() {
        String phoneNumber = "+1234567890";
        String key = "blacklist:" + phoneNumber;
        when(redisTemplate.hasKey(key)).thenReturn(true);

        assertTrue(blacklistCache.isBlacklisted(phoneNumber));
        verify(redisTemplate).hasKey(key);
    }

    @Test
    public void testIsBlacklistedReturnsFalseWhenPhoneNumberDoesNotExist() {
        String phoneNumber = "+1234567890";
        String key = "blacklist:" + phoneNumber;
        when(redisTemplate.hasKey(key)).thenReturn(false);

        assertFalse(blacklistCache.isBlacklisted(phoneNumber));
        verify(redisTemplate).hasKey(key);
    }

    @Test
    public void testAddToBlacklist() {
        String phoneNumber = "+1234567890";
        String key = "blacklist:" + phoneNumber;
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        blacklistCache.addToBlacklist(phoneNumber);

        verify(redisTemplate).opsForValue();
        verify(valueOps).set(key, "1");
    }

    @Test
    public void testRemoveFromBlacklist() {
        String phoneNumber = "+1234567890";
        String key = "blacklist:" + phoneNumber;

        blacklistCache.removeFromBlacklist(phoneNumber);

        verify(redisTemplate).delete(key);
    }
}

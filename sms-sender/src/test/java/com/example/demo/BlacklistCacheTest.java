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

/**
 * Unit tests for BlacklistCache.
 * 
 * This test class verifies that phone number blacklist operations work correctly
 * with Redis as the backing store.
 * 
 * Testing Strategy:
 * - Blacklist checking: Verify phone numbers are correctly identified as blacklisted/not blacklisted
 * - Adding to blacklist: Verify phone numbers can be added to the blacklist
 * - Removing from blacklist: Verify phone numbers can be removed from the blacklist
 * - Redis key format: Verify the correct Redis key format is used
 * 
 * Mocked Dependencies:
 * - StringRedisTemplate: Mocked to avoid actual Redis connections during tests
 * - ValueOperations: Mocked Redis value operations
 * 
 * Redis Key Format: "blacklist:{phoneNumber}"
 */
@ExtendWith(MockitoExtension.class)
public class BlacklistCacheTest {

    // Mock the Redis template to avoid actual Redis connections during tests
    // This allows us to test blacklist logic without a running Redis instance
    @Mock
    private StringRedisTemplate redisTemplate;

    // Mock Redis value operations (used for setting values in Redis)
    @Mock
    private ValueOperations<String, String> valueOps;

    // The BlacklistCache instance under test
    private BlacklistCache blacklistCache;

    /**
     * Set up test environment before each test.
     * 
     * Creates a new BlacklistCache instance with the mocked Redis template.
     * This ensures each test starts with a fresh cache instance.
     */
    @BeforeEach
    public void setUp() {
        blacklistCache = new BlacklistCache(redisTemplate);
    }

    /**
     * Tests that isBlacklisted returns true when a phone number exists in Redis.
     * 
     * This test verifies:
     * 1. The cache correctly checks Redis for blacklisted numbers
     * 2. The correct Redis key format is used ("blacklist:{phoneNumber}")
     * 3. When a key exists in Redis, the number is correctly identified as blacklisted
     * 
     * This is the core functionality - identifying blacklisted numbers.
     */
    @Test
    public void testIsBlacklistedReturnsTrueWhenPhoneNumberExists() {
        // Arrange: Set up the phone number and expected Redis key
        String phoneNumber = "+1234567890";
        String key = "blacklist:" + phoneNumber;  // Expected Redis key format
        
        // Mock Redis to return true (key exists = number is blacklisted)
        when(redisTemplate.hasKey(key)).thenReturn(true);

        // Act: Check if the phone number is blacklisted
        // Assert: Verify it returns true (number is blacklisted)
        assertTrue(blacklistCache.isBlacklisted(phoneNumber));
        
        // Verify: Confirm Redis was checked with the correct key
        // This ensures the cache uses the expected key format
        verify(redisTemplate).hasKey(key);
    }

    /**
     * Tests that isBlacklisted returns false when a phone number does not exist in Redis.
     * 
     * This test verifies:
     * 1. When a key doesn't exist in Redis, the number is not blacklisted
     * 2. The cache correctly identifies non-blacklisted numbers
     * 3. The correct Redis key format is used for the check
     * 
     * This is important - non-blacklisted numbers should return false.
     */
    @Test
    public void testIsBlacklistedReturnsFalseWhenPhoneNumberDoesNotExist() {
        // Arrange: Set up the phone number and expected Redis key
        String phoneNumber = "+1234567890";
        String key = "blacklist:" + phoneNumber;
        
        // Mock Redis to return false (key doesn't exist = number is NOT blacklisted)
        when(redisTemplate.hasKey(key)).thenReturn(false);

        // Act: Check if the phone number is blacklisted
        // Assert: Verify it returns false (number is not blacklisted)
        assertFalse(blacklistCache.isBlacklisted(phoneNumber));
        
        // Verify: Confirm Redis was checked with the correct key
        verify(redisTemplate).hasKey(key);
    }

    /**
     * Tests that phone numbers can be added to the blacklist.
     * 
     * This test verifies:
     * 1. The addToBlacklist method correctly stores a number in Redis
     * 2. The correct Redis key format is used
     * 3. The value "1" is stored (indicates blacklisted)
     * 4. The correct Redis operations are called
     * 
     * This is important for managing the blacklist (adding numbers that should be blocked).
     */
    @Test
    public void testAddToBlacklist() {
        // Arrange: Set up the phone number and expected Redis key
        String phoneNumber = "+1234567890";
        String key = "blacklist:" + phoneNumber;
        
        // Mock Redis value operations
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // Act: Add the phone number to the blacklist
        blacklistCache.addToBlacklist(phoneNumber);

        // Assert: Verify the correct Redis operations were called
        // First, verify opsForValue() was called to get value operations
        verify(redisTemplate).opsForValue();
        // Then, verify set() was called with the correct key and value "1"
        // The value "1" indicates the number is blacklisted
        verify(valueOps).set(key, "1");
    }

    /**
     * Tests that phone numbers can be removed from the blacklist.
     * 
     * This test verifies:
     * 1. The removeFromBlacklist method correctly deletes the key from Redis
     * 2. The correct Redis key format is used
     * 3. The delete operation is called with the correct key
     * 
     * This is important for managing the blacklist (removing numbers that should no longer be blocked).
     */
    @Test
    public void testRemoveFromBlacklist() {
        // Arrange: Set up the phone number and expected Redis key
        String phoneNumber = "+1234567890";
        String key = "blacklist:" + phoneNumber;

        // Act: Remove the phone number from the blacklist
        blacklistCache.removeFromBlacklist(phoneNumber);

        // Assert: Verify the delete operation was called with the correct key
        // Deleting the key removes the number from the blacklist
        verify(redisTemplate).delete(key);
    }
}

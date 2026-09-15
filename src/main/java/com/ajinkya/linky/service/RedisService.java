package com.ajinkya.linky.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(String shortCode, String originalUrl) {
        redisTemplate.opsForValue().set(shortCode, originalUrl);
    }

    public Optional<String> get(String shortCode) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(shortCode));
    }

    public void delete(String shortCode) {
        redisTemplate.delete(shortCode);
    }
}

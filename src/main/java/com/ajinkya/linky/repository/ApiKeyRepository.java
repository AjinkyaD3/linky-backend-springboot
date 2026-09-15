package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.ApiKey;
import com.ajinkya.linky.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findByUser(User user);
    Optional<ApiKey> findByKeyValue(String keyValue);

    @Modifying
    void deleteByUser(User user);
}

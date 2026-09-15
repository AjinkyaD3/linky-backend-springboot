package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.RefreshToken;
import com.ajinkya.linky.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    
    @Modifying
    void deleteByUser(User user);
    
    @Modifying
    void deleteByToken(String token);
}

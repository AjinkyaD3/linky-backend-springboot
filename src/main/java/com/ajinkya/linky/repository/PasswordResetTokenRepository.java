package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.PasswordResetToken;
import com.ajinkya.linky.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUser(User user);
}

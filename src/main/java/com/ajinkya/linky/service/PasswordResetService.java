package com.ajinkya.linky.service;

import com.ajinkya.linky.entity.PasswordResetToken;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.repository.PasswordResetTokenRepository;
import com.ajinkya.linky.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void createResetToken(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("Password reset requested for non-existent email: {}", email);
            return; // Don't reveal user existence
        }

        // Delete any existing tokens for this user
        tokenRepository.deleteByUser(user);

        String tokenString = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .token(tokenString)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        tokenRepository.save(token);

        // For now, just log the reset link instead of real email sending
        log.info("PASSWORD RESET LINK: http://localhost:3000/reset-password?token={}", tokenString);
    }

    public void resetPassword(String tokenString, String newPassword) {
        PasswordResetToken token = tokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(token);
            throw new IllegalArgumentException("Reset token has expired");
        }

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        tokenRepository.delete(token);
    }
}

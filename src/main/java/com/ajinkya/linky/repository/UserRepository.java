package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);

    /** Lowercases and trims, so "Me@X.com " and "me@x.com" are the same account. */
    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Looks a user up by the email they typed, ignoring case. Accounts created before emails were
     * normalised may be stored in mixed case, so the exact match is tried first.
     */
    default Optional<User> findByEmailForLogin(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String trimmed = email.trim();
        return findByEmail(trimmed).or(() -> findFirstByEmailIgnoreCaseOrderByIdAsc(trimmed));
    }
}

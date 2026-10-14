package com.ajinkya.linky.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token", columnList = "token", unique = true),
        // deleteByUser runs on logout and on account deletion.
        @Index(name = "idx_refresh_token_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 (hex) of the current refresh token; the token itself is never stored. */
    @Column(nullable = false, unique = true)
    private String token;

    /** Hash of the token this session held before the last rotation, kept to detect replay of a stolen token. */
    @Column(name = "previous_token", length = 64)
    private String previousToken;

    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}

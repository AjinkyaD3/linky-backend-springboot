package com.ajinkya.linky.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** A registered webhook endpoint. {@code secret} signs every delivery (HMAC-SHA256). */
@Entity
@Table(name = "webhooks", indexes = {
        @Index(name = "idx_webhook_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 64)
    private String secret;

    /** Comma-separated event names this endpoint subscribes to. */
    @Column(nullable = false, length = 200)
    private String events;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "disabled_reason", length = 200)
    private String disabledReason;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Set<String> eventSet() {
        return Arrays.stream(events.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean subscribesTo(String event) {
        return eventSet().contains(event);
    }
}

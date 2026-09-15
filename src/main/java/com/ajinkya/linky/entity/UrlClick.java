package com.ajinkya.linky.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "url_clicks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrlClick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "url_id", nullable = false)
    private Url url;

    private LocalDateTime clickedAt;

    private String browser;
    private String deviceType;
    private String operatingSystem;
    private String country;
    private String city;
    private String referrer;
    private String ipHash;

    @PrePersist
    public void prePersist() {
        if (clickedAt == null) {
            clickedAt = LocalDateTime.now();
        }
    }
}

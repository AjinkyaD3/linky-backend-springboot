package com.ajinkya.linky.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One button on a bio page. {@code shortCode} is a regular Linky link, so clicks are tracked. */
@Entity
@Table(name = "bio_page_links", indexes = {
        @Index(name = "idx_bio_link_page", columnList = "page_id")
})
@Getter
@Setter
@NoArgsConstructor
public class BioPageLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false)
    private BioPage page;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(nullable = false)
    private int position;
}

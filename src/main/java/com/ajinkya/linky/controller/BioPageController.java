package com.ajinkya.linky.controller;

import com.ajinkya.linky.dto.BioPageDtos.*;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.service.BioPageService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/** Owner CRUD under /api/pages; the public read is under /api/public/pages (no auth). */
@RestController
public class BioPageController {

    private final BioPageService service;

    public BioPageController(BioPageService service) {
        this.service = service;
    }

    @GetMapping("/api/pages")
    public List<OwnerPage> mine(@AuthenticationPrincipal User user) {
        return service.mine(user);
    }

    @PostMapping("/api/pages")
    public ResponseEntity<OwnerPage> create(@AuthenticationPrincipal User user, @Valid @RequestBody PageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user, request));
    }

    @PutMapping("/api/pages/{id}")
    public OwnerPage update(@AuthenticationPrincipal User user, @PathVariable Long id,
                            @Valid @RequestBody PageRequest request) {
        return service.update(user, id, request);
    }

    @DeleteMapping("/api/pages/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/public/pages/{slug}")
    public ResponseEntity<PublicPage> publicPage(@PathVariable String slug) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(service.publicView(slug));
    }
}

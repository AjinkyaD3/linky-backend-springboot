package com.ajinkya.linky.controller;

import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.repository.UrlClickRepository;
import com.ajinkya.linky.repository.UrlRepository;
import com.ajinkya.linky.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final UrlRepository urlRepository;
    private final UrlClickRepository urlClickRepository;

    public AdminController(UserRepository userRepository, UrlRepository urlRepository, UrlClickRepository urlClickRepository) {
        this.userRepository = userRepository;
        this.urlRepository = urlRepository;
        this.urlClickRepository = urlClickRepository;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalUrls", urlRepository.count());
        stats.put("totalClicks", urlClickRepository.count());
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @Transactional
    @DeleteMapping("/urls/{id}")
    public ResponseEntity<Void> forceDeleteUrl(@PathVariable Long id) {
        // Use native queries to bypass the @SQLRestriction("is_deleted = false") on the Url entity
        urlRepository.forceDeleteUrlClicks(id);
        urlRepository.forceDeleteUrlTags(id);
        urlRepository.forceDeleteUrl(id);
        
        return ResponseEntity.noContent().build();
    }
}

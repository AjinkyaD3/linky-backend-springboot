package com.ajinkya.linky.controller;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.exception.ResourceNotFoundException;
import com.ajinkya.linky.repository.UrlRepository;
import com.ajinkya.linky.service.ClickService;
import com.ajinkya.linky.service.RedisService;
import com.ajinkya.linky.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.entity.Visibility;
import com.ajinkya.linky.dto.UnlockRequest;
import com.ajinkya.linky.dto.UnlockResponse;
import jakarta.validation.Valid;

import java.net.URI;
import java.util.Map;

@RestController
public class RedirectController {

    private final UrlService urlService;
    private final ClickService clickService;
    private final UrlRepository urlRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisService redisService;

    public RedirectController(UrlService urlService, ClickService clickService, UrlRepository urlRepository, PasswordEncoder passwordEncoder, RedisService redisService) {
        this.urlService = urlService;
        this.clickService = clickService;
        this.urlRepository = urlRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisService = redisService;
    }

    /**
     * Redirect to original URL
     * Example:
     * http://localhost:8080/abc123
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirect(@PathVariable String shortCode, HttpServletRequest request) {

        String originalUrl = urlService.getOriginalUrl(shortCode);
        
        Url url = urlRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new ResourceNotFoundException("Not found"));
            
        if (url.getVisibility() == Visibility.PRIVATE) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authRequired", true));
            }
            User currentUser = (User) auth.getPrincipal();
            if (url.getUser() == null || !url.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("authRequired", true));
            }
        }
        
        if (url.getPasswordHash() != null && !url.getPasswordHash().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("passwordRequired", true));
        }

        if (url.getIsOneTime()) {
            url.setIsActive(false);
            urlRepository.save(url);
            redisService.delete(shortCode);
        }
        
        clickService.logClick(url, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(originalUrl));

        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
    
    @PostMapping("/{shortCode}/unlock")
    public ResponseEntity<UnlockResponse> unlockUrl(@PathVariable String shortCode, @Valid @RequestBody UnlockRequest request) {
        Url url = urlRepository.findByShortCode(shortCode)
            .orElseThrow(() -> new ResourceNotFoundException("Not found"));
            
        if (url.getPasswordHash() == null || url.getPasswordHash().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        if (!passwordEncoder.matches(request.getPassword(), url.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.ok(new UnlockResponse(urlService.getOriginalUrl(shortCode)));
    }
}

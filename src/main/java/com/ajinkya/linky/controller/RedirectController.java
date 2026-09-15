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
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;

@RestController
public class RedirectController {

    private final UrlService urlService;
    private final ClickService clickService;
    private final UrlRepository urlRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisService redisService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

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

        String originalUrl;
        try {
            originalUrl = urlService.getOriginalUrl(shortCode);
        } catch (ResourceNotFoundException e) {
            return frontendErrorRedirect(reasonFor(e.getMessage()));
        }

        Url url;
        try {
            url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Not found"));
        } catch (ResourceNotFoundException e) {
            return frontendErrorRedirect("not_found");
        }

        if (url.getVisibility() == Visibility.PRIVATE) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
                return loginRedirect(shortCode);
            }
            User currentUser = (User) auth.getPrincipal();
            if (url.getUser() == null || !url.getUser().getId().equals(currentUser.getId())) {
                return loginRedirect(shortCode);
            }
        }

        if (url.getPasswordHash() != null && !url.getPasswordHash().isEmpty()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(frontendUrl + "/unlock/" + shortCode));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
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

    private ResponseEntity<Void> frontendErrorRedirect(String reason) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(frontendUrl + "/link-error?reason=" + reason));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    private ResponseEntity<Void> loginRedirect(String shortCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(frontendUrl + "/login?next=" + shortCode));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    private String reasonFor(String exceptionMessage) {
        if (exceptionMessage == null) {
            return "not_found";
        }
        if (exceptionMessage.contains("expired")) {
            return "expired";
        }
        if (exceptionMessage.contains("not active")) {
            return "inactive";
        }
        return "not_found";
    }
}

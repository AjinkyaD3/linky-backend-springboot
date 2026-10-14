package com.ajinkya.linky.controller;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.exception.ResourceNotFoundException;
import com.ajinkya.linky.repository.UrlRepository;
import com.ajinkya.linky.dto.CachedLink;
import com.ajinkya.linky.service.ClickEventPublisher;
import com.ajinkya.linky.service.LinkCache;
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
    private final ClickEventPublisher clickPublisher;
    private final UrlRepository urlRepository;
    private final PasswordEncoder passwordEncoder;
    private final LinkCache linkCache;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public RedirectController(UrlService urlService, ClickEventPublisher clickPublisher, UrlRepository urlRepository, PasswordEncoder passwordEncoder, LinkCache linkCache) {
        this.urlService = urlService;
        this.clickPublisher = clickPublisher;
        this.urlRepository = urlRepository;
        this.passwordEncoder = passwordEncoder;
        this.linkCache = linkCache;
    }

    /**
     * Redirect to original URL
     * Example:
     * http://localhost:8080/abc123
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<?> redirect(@PathVariable String shortCode, HttpServletRequest request) {

        // Hot path: L1 -> L2 -> database. Almost every request is served from memory.
        CachedLink link = linkCache.get(shortCode, () -> loadFromDatabase(shortCode));

        if (link.missing()) {
            return frontendErrorRedirect("not_found");
        }
        if (link.isExpired()) {
            return frontendErrorRedirect("expired");
        }
        if (!link.active()) {
            return frontendErrorRedirect("inactive");
        }

        if (link.isPrivate()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
                return loginRedirect(shortCode);
            }
            User currentUser = (User) auth.getPrincipal();
            if (link.ownerId() == null || !link.ownerId().equals(currentUser.getId())) {
                return loginRedirect(shortCode);
            }
        }

        if (link.hasPassword()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(frontendUrl + "/unlock/" + shortCode));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);
        }

        if (link.oneTime()) {
            // Atomic claim: only one concurrent request can consume a one-time link.
            if (urlRepository.deactivateIfActive(link.id()) == 0) {
                return frontendErrorRedirect("inactive");
            }
            linkCache.invalidate(shortCode);
        }

        clickPublisher.publish(link.id(), request);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(link.originalUrl()));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    private CachedLink loadFromDatabase(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .map(url -> new CachedLink(
                        url.getId(),
                        url.getOriginalUrl(),
                        Boolean.TRUE.equals(url.getIsActive()),
                        Boolean.TRUE.equals(url.getIsOneTime()),
                        url.getPasswordHash() != null && !url.getPasswordHash().isEmpty(),
                        url.getVisibility() == Visibility.PRIVATE,
                        url.getUser() == null ? null : url.getUser().getId(),
                        url.getExpiresAt() == null ? null
                                : url.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond(),
                        false))
                .orElseGet(CachedLink::notFound);
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
        
        if (url.getIsOneTime()) {
            if (urlRepository.deactivateIfActive(url.getId()) == 0) {
                throw new ResourceNotFoundException("Link is no longer active");
            }
            linkCache.invalidate(shortCode);
            return ResponseEntity.ok(new UnlockResponse(url.getOriginalUrl()));
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

}

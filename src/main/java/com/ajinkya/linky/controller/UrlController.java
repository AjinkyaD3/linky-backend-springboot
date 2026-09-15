package com.ajinkya.linky.controller;

import com.ajinkya.linky.dto.CreateUrlRequest;
import com.ajinkya.linky.dto.UrlResponse;
import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.service.UrlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.ajinkya.linky.dto.UpdateUrlRequest;
import com.ajinkya.linky.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

import com.ajinkya.linky.service.QRCodeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/url")
public class UrlController {

    private final UrlService urlService;
    private final QRCodeService qrCodeService;

    public UrlController(UrlService urlService, QRCodeService qrCodeService) {
        this.urlService = urlService;
        this.qrCodeService = qrCodeService;
    }

    /**
     * Get URL by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UrlResponse> getUrlById(@PathVariable Long id, @AuthenticationPrincipal User user) {

        return urlService.findResponseByIdAndUser(id, user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new short URL
     */
    @PostMapping
    public ResponseEntity<UrlResponse> createUrl(@Valid @RequestBody CreateUrlRequest request) {

        User user = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            user = (User) authentication.getPrincipal();
        }

        UrlResponse savedUrl = urlService.createShortUrl(request, user);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedUrl);
    }

    @GetMapping("/my")
    public ResponseEntity<List<UrlResponse>> getMyUrls(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String tag) {
        return ResponseEntity.ok(urlService.getUserUrlsFiltered(user, search, sort, tag));
    }

    @GetMapping("/my/favorites")
    public ResponseEntity<List<UrlResponse>> getMyFavoriteUrls(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(urlService.getUserFavoriteUrls(user));
    }

    @GetMapping("/my/archived")
    public ResponseEntity<List<UrlResponse>> getMyArchivedUrls(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(urlService.getUserArchivedUrls(user));
    }

    @PutMapping("/{shortCode}/favorite")
    public ResponseEntity<UrlResponse> toggleFavorite(@PathVariable String shortCode, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(urlService.toggleFavorite(shortCode, user));
    }

    @PutMapping("/{shortCode}/archive")
    public ResponseEntity<UrlResponse> toggleArchive(@PathVariable String shortCode, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(urlService.toggleArchive(shortCode, user));
    }

    @PostMapping("/{shortCode}/duplicate")
    public ResponseEntity<UrlResponse> duplicateUrl(@PathVariable String shortCode, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(urlService.duplicateUrl(shortCode, user));
    }

    @PutMapping("/{shortCode}")
    public ResponseEntity<UrlResponse> updateUrlDetails(
            @PathVariable String shortCode,
            @Valid @RequestBody UpdateUrlRequest request,
            @AuthenticationPrincipal User user) {

        UrlResponse updated = urlService.updateUrlDetails(shortCode, request, user);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{shortCode}/qrcode")
    public ResponseEntity<byte[]> getQRCode(
            @PathVariable String shortCode,
            @RequestParam(required = false, defaultValue = "png") String format) {
        // Just verify it exists
        if (!urlService.existsByShortCode(shortCode)) {
            return ResponseEntity.notFound().build();
        }

        String url = "http://localhost:8080/" + shortCode; // We should probably make base URL configurable later
        
        if ("svg".equalsIgnoreCase(format)) {
            String svg = qrCodeService.generateQRCodeSvg(url, 250, 250);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"qrcode.svg\"")
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .body(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            byte[] qrImage = qrCodeService.generateQRCodeImage(url, 250, 250);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"qrcode.png\"")
                    .contentType(MediaType.IMAGE_PNG)
                    .body(qrImage);
        }
    }

    /**
     * Delete URL — must be the owner
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUrl(@PathVariable Long id, @AuthenticationPrincipal User user) {

        // Verify ownership
        Url url = urlService.findById(id).orElse(null);
        if (url == null) {
            return ResponseEntity.notFound().build();
        }
        if (url.getUser() == null || !url.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }

        urlService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
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
    private final jakarta.validation.Validator validator;

    @org.springframework.beans.factory.annotation.Value("${app.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    public UrlController(UrlService urlService, QRCodeService qrCodeService, jakarta.validation.Validator validator) {
        this.urlService = urlService;
        this.qrCodeService = qrCodeService;
        this.validator = validator;
    }

    /**
     * Get URL by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UrlResponse> getUrlById(@PathVariable Long id, @AuthenticationPrincipal User user) {

        return urlService.findResponseByIdAndUser(id, user)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new com.ajinkya.linky.exception.ResourceNotFoundException("URL not found"));
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

    /**
     * Create up to 100 links in one call. Each row succeeds or fails on its own, so one bad URL
     * does not lose the rest; the response has one result per input row, in order.
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<com.ajinkya.linky.dto.BulkResult>> bulkCreate(
            @Valid @RequestBody com.ajinkya.linky.dto.BulkCreateRequest request,
            @AuthenticationPrincipal User user) {

        List<com.ajinkya.linky.dto.BulkResult> results = new java.util.ArrayList<>();
        List<CreateUrlRequest> rows = request.getLinks();
        for (int i = 0; i < rows.size(); i++) {
            CreateUrlRequest row = rows.get(i);
            try {
                if (row == null) {
                    throw new IllegalArgumentException("Empty row");
                }
                var violations = validator.validate(row);
                if (!violations.isEmpty()) {
                    throw new IllegalArgumentException(violations.iterator().next().getMessage());
                }
                results.add(new com.ajinkya.linky.dto.BulkResult(i, true, urlService.createShortUrl(row, user), null));
            } catch (RuntimeException e) {
                results.add(new com.ajinkya.linky.dto.BulkResult(i, false, null, e.getMessage()));
            }
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/my")
    public ResponseEntity<List<UrlResponse>> getMyUrls(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var result = urlService.getUserUrlsPage(user, search, sort, tag, page, size);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @GetMapping("/my/favorites")
    public ResponseEntity<List<UrlResponse>> getMyFavoriteUrls(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var result = urlService.getUserFavoriteUrls(user, page, size);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
    }

    @GetMapping("/my/archived")
    public ResponseEntity<List<UrlResponse>> getMyArchivedUrls(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var result = urlService.getUserArchivedUrls(user, page, size);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(result.getTotalElements()))
                .body(result.getContent());
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

    /**
     * QR code for a short link. Options: format=png|svg, size (100-1000), fg / bg (hex colours),
     * margin (0-10 quiet-zone modules), download=true to force a file download.
     */
    @GetMapping("/{shortCode}/qrcode")
    public ResponseEntity<byte[]> getQRCode(
            @PathVariable String shortCode,
            @RequestParam(required = false, defaultValue = "png") String format,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String fg,
            @RequestParam(required = false) String bg,
            @RequestParam(required = false) Integer margin,
            @RequestParam(required = false, defaultValue = "false") boolean download) {
        if (!urlService.existsByShortCode(shortCode)) {
            throw new com.ajinkya.linky.exception.ResourceNotFoundException("Short code not found");
        }

        QRCodeService.Style style = qrCodeService.style(size, fg, bg, margin);
        String target = publicBaseUrl.replaceAll("/+$", "") + "/" + shortCode;
        String disposition = download ? "attachment" : "inline";

        if ("svg".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + shortCode + ".svg\"")
                    .contentType(MediaType.valueOf("image/svg+xml"))
                    .body(qrCodeService.generateSvg(target, style).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + shortCode + ".png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(qrCodeService.generatePng(target, style));
    }

    /**
     * Delete URL — must be the owner
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUrl(@PathVariable Long id, @AuthenticationPrincipal User user) {

        // Same answer for "does not exist" and "not yours", so ids cannot be probed.
        Url url = urlService.findById(id)
                .filter(u -> u.getUser() != null && u.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new com.ajinkya.linky.exception.ResourceNotFoundException("URL not found"));

        urlService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
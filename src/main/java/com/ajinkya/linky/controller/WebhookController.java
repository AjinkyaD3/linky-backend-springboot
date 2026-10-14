package com.ajinkya.linky.controller;

import com.ajinkya.linky.dto.WebhookDtos.*;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.service.WebhookService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService service;

    public WebhookController(WebhookService service) {
        this.service = service;
    }

    @GetMapping
    public List<WebhookView> list(@AuthenticationPrincipal User user) {
        return service.list(user);
    }

    /** Events a webhook can subscribe to. */
    @GetMapping("/events")
    public Set<String> events() {
        return WebhookService.SUBSCRIBABLE;
    }

    /** The response includes the signing secret; it is shown once and cannot be retrieved later. */
    @PostMapping
    public ResponseEntity<WebhookView> create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user, request));
    }

    @PutMapping("/{id}")
    public WebhookView update(@AuthenticationPrincipal User user, @PathVariable Long id,
                              @Valid @RequestBody UpdateRequest request) {
        return service.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Void> test(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.sendTest(user, id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/deliveries")
    public List<DeliveryView> deliveries(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.recentDeliveries(user, id);
    }
}

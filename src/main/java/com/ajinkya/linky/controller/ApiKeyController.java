package com.ajinkya.linky.controller;

import com.ajinkya.linky.dto.ApiKeyDto;
import com.ajinkya.linky.dto.CreateApiKeyRequest;
import com.ajinkya.linky.entity.ApiKey;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.exception.ResourceNotFoundException;
import com.ajinkya.linky.repository.ApiKeyRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/keys")
public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    public ApiKeyController(ApiKeyRepository apiKeyRepository, PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyDto>> getApiKeys(@AuthenticationPrincipal User user) {
        List<ApiKeyDto> keys = apiKeyRepository.findByUser(user).stream().map(key -> 
            ApiKeyDto.builder()
                .id(key.getId())
                .name(key.getName())
                // Mask the key for security in the list view
                .keyValue(key.getKeyValue().substring(0, 8) + "****************")
                .createdAt(key.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
        
        return ResponseEntity.ok(keys);
    }

    @PostMapping
    public ResponseEntity<ApiKeyDto> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request,
            @AuthenticationPrincipal User user) {
        
        ApiKey apiKey = ApiKey.builder()
                .name(request.getName())
                .keyValue("temp_" + UUID.randomUUID().toString()) // placeholder
                .user(user)
                .build();
                
        apiKey = apiKeyRepository.save(apiKey); // Get ID
        
        String secret = UUID.randomUUID().toString().replace("-", "");
        String plainKey = "lk_" + apiKey.getId() + "_" + secret;
        
        apiKey.setKeyValue(passwordEncoder.encode(plainKey));
        apiKey = apiKeyRepository.save(apiKey);
        
        ApiKeyDto response = ApiKeyDto.builder()
                .id(apiKey.getId())
                .name(apiKey.getName())
                .keyValue(plainKey) // Return full key only once on creation
                .createdAt(apiKey.getCreatedAt())
                .build();
                
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeApiKey(@PathVariable Long id, @AuthenticationPrincipal User user) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API Key not found"));
                
        if (!apiKey.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        apiKeyRepository.delete(apiKey);
        return ResponseEntity.noContent().build();
    }
}

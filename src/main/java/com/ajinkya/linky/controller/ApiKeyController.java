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
import com.ajinkya.linky.util.Hashing;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/keys")
public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyController(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyDto>> getApiKeys(@AuthenticationPrincipal User user) {
        List<ApiKeyDto> keys = apiKeyRepository.findByUser(user).stream().map(key -> 
            ApiKeyDto.builder()
                .id(key.getId())
                .name(key.getName())
                // Only a hash is stored; show the public prefix so the user can tell keys apart
                .keyValue("lk_" + key.getId() + "_****************")
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
        
        // a random 128-bit secret: SHA-256 is enough (see ApiKeyAuthenticationFilter)
        apiKey.setKeyValue(Hashing.sha256Hex(plainKey));
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
            // same answer as "does not exist": do not reveal that someone else's key id exists
            throw new ResourceNotFoundException("API Key not found");
        }
        
        apiKeyRepository.delete(apiKey);
        return ResponseEntity.noContent().build();
    }
}

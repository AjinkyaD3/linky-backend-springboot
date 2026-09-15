package com.ajinkya.linky.controller;

import com.ajinkya.linky.dto.ChangePasswordRequest;
import com.ajinkya.linky.dto.UpdateProfileRequest;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.repository.ApiKeyRepository;
import com.ajinkya.linky.repository.PasswordResetTokenRepository;
import com.ajinkya.linky.repository.RefreshTokenRepository;
import com.ajinkya.linky.repository.TagRepository;
import com.ajinkya.linky.repository.UrlRepository;
import com.ajinkya.linky.repository.UserRepository;
import com.ajinkya.linky.service.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final UrlRepository urlRepository;
    private final TagRepository tagRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, FileStorageService fileStorageService,
                           UrlRepository urlRepository, TagRepository tagRepository, ApiKeyRepository apiKeyRepository,
                           RefreshTokenRepository refreshTokenRepository, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.urlRepository = urlRepository;
        this.tagRepository = tagRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    public ResponseEntity<User> updateProfile(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateProfileRequest request) {
        user.setName(request.getName());
        if (request.getProfileImage() != null) {
            user.setProfileImage(request.getProfileImage());
        }
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal User user, @Valid @RequestBody ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Incorrect current password");
        }
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    @Transactional
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal User user) {
        // Clear everything that has a foreign key back to this user before deleting it -
        // none of these relations cascade at the DB or JPA level.
        urlRepository.forceDeleteUrlClicksByUser(user.getId());
        urlRepository.forceDeleteUrlTagsByUser(user.getId());
        urlRepository.forceDeleteUrlsByUser(user.getId());
        tagRepository.deleteByUser(user);
        apiKeyRepository.deleteByUser(user);
        refreshTokenRepository.deleteByUser(user);
        passwordResetTokenRepository.deleteByUser(user);
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(@AuthenticationPrincipal User user, @RequestParam("file") MultipartFile file) {
        String avatarUrl = fileStorageService.storeAvatar(file, user.getId());
        user.setProfileImage(avatarUrl);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("url", avatarUrl));
    }
}

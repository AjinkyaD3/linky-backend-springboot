package com.ajinkya.linky.service;

import com.ajinkya.linky.dto.LoginRequest;
import com.ajinkya.linky.dto.LoginResponse;
import com.ajinkya.linky.dto.RegisterRequest;
import com.ajinkya.linky.entity.RefreshToken;
import com.ajinkya.linky.entity.Role;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.repository.RefreshTokenRepository;
import com.ajinkya.linky.repository.UserRepository;
import com.ajinkya.linky.util.Hashing;
import com.ajinkya.linky.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Registration, login and session handling.
 *
 * Refresh tokens are stored only as SHA-256 hashes. Every refresh rotates the token and remembers the
 * previous one: if an already-rotated token shows up again after the grace period it was almost certainly
 * copied, so the whole session is revoked (token-reuse detection). A short grace period tolerates two
 * browser tabs refreshing at nearly the same moment.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int REFRESH_DAYS = 7;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration reuseGrace;
    /** Compared against when the email is unknown, so login timing does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       RefreshTokenRepository refreshTokenRepository,
                       @Value("${app.auth.refresh-reuse-grace-seconds:30}") long reuseGraceSeconds) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
        this.reuseGrace = Duration.ofSeconds(reuseGraceSeconds);
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    public void register(RegisterRequest request) {
        String email = UserRepository.normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailForLogin(request.getEmail()).orElse(null);
        boolean ok = passwordEncoder.matches(request.getPassword(), user == null ? dummyHash : user.getPassword());
        if (user == null || !ok) {
            throw new BadCredentialsException("Invalid email or password");
        }

        String refreshTokenString = jwtUtil.generateRefreshToken(user.getEmail());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(Hashing.sha256Hex(refreshTokenString))
                .expiresAt(LocalDateTime.now().plusDays(REFRESH_DAYS))
                .build());

        return response(user, jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name()), refreshTokenString);
    }

    /**
     * Rotates a refresh token. {@code noRollbackFor} matters: when replay is detected we revoke the session
     * and then throw, and that revocation must not be rolled back by the exception.
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public LoginResponse refreshToken(String tokenString) {
        if (!jwtUtil.isValidRefreshToken(tokenString)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String presented = Hashing.sha256Hex(tokenString);
        RefreshToken session = refreshTokenRepository.findByToken(presented).orElse(null);

        if (session == null) {
            // Not the current token. Was it a token this session already rotated away from?
            RefreshToken replayed = refreshTokenRepository.findByPreviousToken(presented).orElse(null);
            if (replayed != null && withinGrace(replayed)) {
                session = replayed; // two tabs refreshing together: rotate again from the same session
            } else {
                if (replayed != null) {
                    refreshTokenRepository.delete(replayed);
                    log.warn("Refresh token reuse detected for user {}; session revoked", replayed.getUser().getId());
                }
                throw new IllegalArgumentException("Invalid refresh token");
            }
        }

        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(session);
            throw new IllegalArgumentException("Refresh token expired");
        }

        User user = session.getUser();
        String newRefresh = jwtUtil.generateRefreshToken(user.getEmail());

        session.setPreviousToken(session.getToken());
        session.setToken(Hashing.sha256Hex(newRefresh));
        session.setRotatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_DAYS));
        refreshTokenRepository.save(session);

        return response(user, jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name()), newRefresh);
    }

    public void logout(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            return;
        }
        String hash = Hashing.sha256Hex(tokenString);
        refreshTokenRepository.findByToken(hash)
                .or(() -> refreshTokenRepository.findByPreviousToken(hash))
                .ifPresent(refreshTokenRepository::delete);
    }

    /** Ends every session of a user, e.g. after a password change or reset. */
    public void revokeAllSessions(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    private boolean withinGrace(RefreshToken session) {
        return session.getRotatedAt() != null
                && session.getRotatedAt().plus(reuseGrace).isAfter(LocalDateTime.now());
    }

    private static LoginResponse response(User user, String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}

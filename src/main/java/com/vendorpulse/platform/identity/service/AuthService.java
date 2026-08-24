package com.vendorpulse.platform.identity.service;

import com.vendorpulse.platform.common.exception.DomainExceptions.AuthenticationFailedException;
import com.vendorpulse.platform.common.exception.DomainExceptions.DuplicateResourceException;
import com.vendorpulse.platform.config.JwtProperties;
import com.vendorpulse.platform.config.JwtTokenProvider;
import com.vendorpulse.platform.identity.dto.AuthResponse;
import com.vendorpulse.platform.identity.dto.LoginRequest;
import com.vendorpulse.platform.identity.dto.RegisterRequest;
import com.vendorpulse.platform.identity.entity.RefreshToken;
import com.vendorpulse.platform.identity.entity.StaffProfile;
import com.vendorpulse.platform.identity.entity.User;
import com.vendorpulse.platform.identity.entity.UserRole;
import com.vendorpulse.platform.identity.entity.Vendor;
import com.vendorpulse.platform.identity.repository.RefreshTokenRepository;
import com.vendorpulse.platform.identity.repository.StaffProfileRepository;
import com.vendorpulse.platform.identity.repository.UserRepository;
import com.vendorpulse.platform.identity.repository.VendorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                        VendorRepository vendorRepository,
                        StaffProfileRepository staffProfileRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        PasswordEncoder passwordEncoder,
                        JwtTokenProvider tokenProvider,
                        JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.vendorRepository = vendorRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Vendor vendor = null;
        if (request.role() == UserRole.OWNER) {
            vendor = Vendor.builder()
                    .legalName(request.orgLegalName() != null ? request.orgLegalName() : "Unnamed Org")
                    .markupPctDefault(new BigDecimal("0.2200"))
                    .billingAddress(Map.of())
                    .build();
            vendor = vendorRepository.save(vendor);
        }

        User user = User.builder()
                .email(request.email())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .orgId(vendor != null ? vendor.getId() : null)
                .active(true)
                .tokenVersion(0)
                .build();
        user = userRepository.save(user);

        if (request.role() == UserRole.STAFF) {
            StaffProfile profile = StaffProfile.builder()
                    .userId(user.getId())
                    .skillTags(List.of())
                    .avgRating(BigDecimal.ZERO)
                    .reliabilityScore(BigDecimal.ONE)
                    .backgroundCheckStatus("PENDING")
                    .noShowCount90d(0)
                    .suspended(false)
                    .build();
            staffProfileRepository.save(profile);
        }

        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid email or password"));

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid email or password");
        }

        return issueTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new AuthenticationFailedException("Refresh token invalid or revoked"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthenticationFailedException("Refresh token expired");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new AuthenticationFailedException("Account no longer exists"));

        // Rotate: revoke the used token and issue a fresh pair.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueTokenPair(user);
    }

    private AuthResponse issueTokenPair(User user) {
        String accessToken = tokenProvider.createAccessToken(user);
        String rawRefreshToken = generateOpaqueToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(rawRefreshToken))
                .expiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlDays() * 24 * 3600))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                jwtProperties.getAccessTokenTtlMinutes() * 60,
                user.getId(),
                user.getRole().name());
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

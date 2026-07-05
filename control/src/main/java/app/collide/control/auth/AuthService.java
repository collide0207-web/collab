package app.collide.control.auth;

import app.collide.control.auth.dto.AuthResponse;
import app.collide.control.auth.dto.ChangePasswordRequest;
import app.collide.control.auth.dto.GoogleLoginRequest;
import app.collide.control.auth.dto.LoginRequest;
import app.collide.control.auth.dto.SignupRequest;
import app.collide.control.auth.dto.UpdateProfileRequest;
import app.collide.control.auth.dto.UserDto;
import app.collide.control.common.ApiException;
import app.collide.control.common.ClientInfo;
import app.collide.control.user.User;
import app.collide.control.user.UserMapper;
import app.collide.control.user.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates every authentication flow: signup, login, Google login, refresh, logout,
 * profile/password management and account deletion. Business rules and security
 * decisions live here; the controller only adapts HTTP <-> DTOs and applies rate limits.
 *
 * Cross-cutting choices:
 *  - Credentials errors are always the generic "invalid credentials" (anti-enumeration).
 *  - A dummy BCrypt compare runs when the email is unknown so response time doesn't leak
 *    whether an account exists (timing-attack defence).
 *  - Failed-login counting + audit are delegated to {@link LoginSecurity}, which commits
 *    them in their own transaction so they survive the thrown 401.
 *  - We log only ids/emails — never passwords, tokens or Google tokens.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final GoogleTokenVerifier google;
    private final LoginSecurity loginSecurity;
    private final UserMapper mapper;

    /** Precomputed hash used to equalise timing on the unknown-email path. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       RefreshTokenService refreshTokens, GoogleTokenVerifier google,
                       LoginSecurity loginSecurity, UserMapper mapper) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.google = google;
        this.loginSecurity = loginSecurity;
        this.mapper = mapper;
        this.dummyHash = encoder.encode("timing-equalizer-" + UUID.randomUUID());
    }

    // --- signup -----------------------------------------------------------

    @Transactional
    public AuthResponse signup(SignupRequest req, ClientInfo client) {
        String email = normalizeEmail(req.email());
        String username = req.username().trim();

        // Friendly pre-checks; the unique constraints below are the real guarantee.
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("email already registered");
        }
        if (users.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("username already taken");
        }

        User user = User.local(UUID.randomUUID(), email, username, req.name().trim(),
                encoder.encode(req.password()));
        try {
            users.saveAndFlush(user); // flush now so a concurrent duplicate surfaces here
        } catch (DataIntegrityViolationException e) {
            // Race: two signups for the same email/username. The unique index rejected one.
            throw ApiException.conflict("email or username already registered");
        }

        loginSecurity.succeeded(user.getId(), email, client); // joins this tx (FK-safe)
        log.info("signup ok userId={} email={}", user.getId(), email);
        return issueTokens(user, client);
    }

    // --- email + password login ------------------------------------------

    public AuthResponse login(LoginRequest req, ClientInfo client) {
        String email = normalizeEmail(req.email());
        User user = users.findByEmail(email).orElse(null);

        if (user == null) {
            encoder.matches(req.password(), dummyHash); // constant-time-ish: burn a compare
            loginSecurity.badCredentials(null, email, client, "no such account");
            throw ApiException.unauthorized("invalid credentials");
        }
        if (user.isLocked()) {
            loginSecurity.rejected(user.getId(), email, client, "account locked");
            throw ApiException.locked("account temporarily locked due to failed logins");
        }

        boolean ok = user.getPasswordHash() != null
                && encoder.matches(req.password(), user.getPasswordHash());
        if (!ok) {
            loginSecurity.badCredentials(user.getId(), email, client, "bad password");
            throw ApiException.unauthorized("invalid credentials");
        }
        if (user.isDeleted() || !user.isActive()) {
            // Only revealed to someone who supplied the correct password (the owner).
            loginSecurity.rejected(user.getId(), email, client, "account disabled");
            throw ApiException.forbidden("account is disabled");
        }

        loginSecurity.succeeded(user.getId(), email, client);
        log.info("login ok userId={}", user.getId());
        return issueTokens(user, client);
    }

    // --- continue with Google --------------------------------------------

    @Transactional
    public AuthResponse loginWithGoogle(GoogleLoginRequest req, ClientInfo client) {
        GoogleTokenVerifier.GoogleUser g = google.verify(req.idToken());
        String email = normalizeEmail(g.email());

        User user = users.findByGoogleId(g.subject())
                .or(() -> users.findByEmail(email))
                .orElse(null);

        if (user == null) {
            user = User.google(UUID.randomUUID(), email,
                    uniqueUsername(usernameSeed(email)), displayName(g, email),
                    g.subject(), g.picture());
            try {
                users.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                // Concurrent create for the same email — adopt the winner.
                user = users.findByEmail(email)
                        .orElseThrow(() -> ApiException.conflict("account creation conflict"));
            }
        } else if (user.getGoogleId() == null) {
            // Existing LOCAL account with this email → link the Google identity to it.
            user.linkGoogle(g.subject(), g.picture());
        }

        if (user.isDeleted() || !user.isActive()) {
            loginSecurity.rejected(user.getId(), email, client, "account disabled");
            throw ApiException.forbidden("account is disabled");
        }

        loginSecurity.succeeded(user.getId(), email, client);
        log.info("google login ok userId={}", user.getId());
        return issueTokens(user, client);
    }

    // --- refresh (rotation) ----------------------------------------------

    @Transactional
    public AuthResponse refresh(String refreshToken, ClientInfo client) {
        RefreshTokenService.Rotation rot = refreshTokens.rotate(refreshToken, client);
        User user = users.findById(rot.userId())
                .orElseThrow(() -> ApiException.unauthorized("account not found"));
        if (user.isDeleted() || !user.isActive()) {
            refreshTokens.revokeAll(user.getId());
            throw ApiException.forbidden("account is disabled");
        }
        String access = jwt.issueAccessToken(user);
        return AuthResponse.of(access, rot.rawToken(), jwt.accessTtlSeconds(), mapper.toDto(user));
    }

    // --- logout ----------------------------------------------------------

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken); // idempotent
    }

    public void logoutAll(UUID userId) {
        int revoked = refreshTokens.revokeAll(userId);
        log.info("logout-all userId={} revoked={}", userId, revoked);
    }

    // --- account ---------------------------------------------------------

    @Transactional(readOnly = true)
    public UserDto me(UUID userId) {
        return users.findById(userId)
                .map(mapper::toDto)
                .orElseThrow(() -> ApiException.notFound("user not found"));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("account not found"));
        if (user.getPasswordHash() == null) {
            throw ApiException.badRequest("this account has no password (signed up with Google)");
        }
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized("current password is incorrect");
        }
        user.changePassword(encoder.encode(req.newPassword()));
        refreshTokens.revokeAll(userId); // invalidate other sessions after a password change
        log.info("password changed userId={}", userId);
    }

    @Transactional
    public UserDto updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("user not found"));
        if (req.name() != null && !req.name().isBlank()) {
            user.rename(req.name().trim());
        }
        if (req.profilePicture() != null) {
            user.setProfilePicture(req.profilePicture().isBlank() ? null : req.profilePicture());
        }
        return mapper.toDto(user);
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("user not found"));
        user.softDelete();
        refreshTokens.revokeAll(userId);
        log.info("account soft-deleted userId={}", userId);
    }

    // --- helpers ----------------------------------------------------------

    private AuthResponse issueTokens(User user, ClientInfo client) {
        String access = jwt.issueAccessToken(user);
        String refresh = refreshTokens.issue(user.getId(), client);
        return AuthResponse.of(access, refresh, jwt.accessTtlSeconds(), mapper.toDto(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String usernameSeed(String email) {
        String local = email.substring(0, Math.max(0, email.indexOf('@')));
        String cleaned = local.replaceAll("[^a-zA-Z0-9_.-]", "");
        if (cleaned.length() < 3) cleaned = "user" + cleaned;
        return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
    }

    private String uniqueUsername(String base) {
        if (!users.existsByUsernameIgnoreCase(base)) return base;
        for (int i = 0; i < 5; i++) {
            String candidate = base + "_" + UUID.randomUUID().toString().substring(0, 4);
            if (!users.existsByUsernameIgnoreCase(candidate)) return candidate;
        }
        return base + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String displayName(GoogleTokenVerifier.GoogleUser g, String email) {
        return (g.name() != null && !g.name().isBlank())
                ? g.name()
                : email.substring(0, Math.max(0, email.indexOf('@')));
    }
}

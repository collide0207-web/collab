package app.collide.control.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.collide.control.auth.dto.AuthResponse;
import app.collide.control.auth.dto.LoginRequest;
import app.collide.control.auth.dto.SignupRequest;
import app.collide.control.auth.dto.UserDto;
import app.collide.control.common.ApiException;
import app.collide.control.common.ClientInfo;
import app.collide.control.user.User;
import app.collide.control.user.UserMapper;
import app.collide.control.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Behavioural unit tests for {@link AuthService} with all collaborators mocked. Focuses
 * on the security-critical branches: duplicate signup, unknown user, bad password,
 * lockout, and the happy path.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @Mock RefreshTokenService refreshTokens;
    @Mock GoogleTokenVerifier google;
    @Mock LoginSecurity loginSecurity;
    @Mock UserMapper mapper;

    private AuthService auth;
    private final ClientInfo client = new ClientInfo(null, "1.2.3.4", "agent");

    @BeforeEach
    void setUp() {
        // Used by the constructor to precompute the timing-equaliser dummy hash.
        when(encoder.encode(anyString())).thenReturn("hashed");
        auth = new AuthService(users, encoder, jwt, refreshTokens, google, loginSecurity, mapper);
    }

    private User localUser(String storedHash) {
        return User.local(UUID.randomUUID(), "alice@example.com", "alice", "Alice", storedHash);
    }

    @Test
    void signupRejectsDuplicateEmail() {
        when(users.existsByEmail("alice@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                auth.signup(new SignupRequest("alice@example.com", "alice", "Alice", "Abcdef1!"), client));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void loginWithUnknownEmailReturnsGenericUnauthorized() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(encoder.matches(anyString(), anyString())).thenReturn(false); // dummy timing compare

        ApiException ex = assertThrows(ApiException.class, () ->
                auth.login(new LoginRequest("alice@example.com", "whatever"), client));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("invalid credentials", ex.getMessage()); // no account enumeration
        verify(loginSecurity).badCredentials(isNull(), eq("alice@example.com"), any(), anyString());
    }

    @Test
    void loginWithBadPasswordCountsAsFailure() {
        User user = localUser("stored");
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "stored")).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () ->
                auth.login(new LoginRequest("alice@example.com", "wrong"), client));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(loginSecurity).badCredentials(eq(user.getId()), eq("alice@example.com"), any(), anyString());
    }

    @Test
    void loginOnLockedAccountIsRejectedWithoutCheckingPassword() {
        User user = localUser("stored");
        user.onLoginFailure(1, 3600); // threshold 1 → immediately locked

        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        ApiException ex = assertThrows(ApiException.class, () ->
                auth.login(new LoginRequest("alice@example.com", "irrelevant"), client));

        assertEquals(HttpStatus.LOCKED, ex.getStatus());
        verify(loginSecurity).rejected(eq(user.getId()), anyString(), any(), anyString());
    }

    @Test
    void loginSuccessIssuesTokens() {
        User user = localUser("stored");
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("Secret1!", "stored")).thenReturn(true);
        when(jwt.issueAccessToken(user)).thenReturn("access.jwt");
        when(jwt.accessTtlSeconds()).thenReturn(900L);
        when(refreshTokens.issue(eq(user.getId()), any())).thenReturn("refresh-raw");
        when(mapper.toDto(user)).thenReturn(new UserDto(
                user.getId().toString(), "alice@example.com", "alice", "Alice",
                "USER", false, null, "LOCAL", Instant.now()));

        AuthResponse resp = auth.login(new LoginRequest("alice@example.com", "Secret1!"), client);

        assertEquals("access.jwt", resp.accessToken());
        assertEquals("refresh-raw", resp.refreshToken());
        assertEquals("Bearer", resp.tokenType());
        assertEquals(900L, resp.expiresIn());
        verify(loginSecurity).succeeded(eq(user.getId()), eq("alice@example.com"), any());
    }
}

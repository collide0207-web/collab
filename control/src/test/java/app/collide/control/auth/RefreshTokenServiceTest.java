package app.collide.control.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.collide.control.common.ApiException;
import app.collide.control.common.ClientInfo;
import app.collide.control.common.Tokens;
import app.collide.control.token.RefreshToken;
import app.collide.control.token.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/** Rotation + reuse-detection logic for {@link RefreshTokenService}, mocking the repo. */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository repo;

    private final ClientInfo client = new ClientInfo(null, "1.2.3.4", "agent");

    private RefreshTokenService service() {
        return new RefreshTokenService(repo, 2_592_000);
    }

    private RefreshToken token(UUID userId, String rawToken, Instant expires) {
        return new RefreshToken(UUID.randomUUID(), userId, Tokens.sha256(rawToken), expires, null, null, null);
    }

    @Test
    void issueStoresHashNotRawToken() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String raw = service().issue(UUID.randomUUID(), client);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(saved.capture());
        assertNotEquals(raw, saved.getValue().getTokenHash(), "raw token must never be stored");
        assertEquals(Tokens.sha256(raw), saved.getValue().getTokenHash());
    }

    @Test
    void rotateRevokesOldAndMintsNew() {
        UUID userId = UUID.randomUUID();
        String raw = Tokens.generate();
        RefreshToken current = token(userId, raw, Instant.now().plusSeconds(1000));
        when(repo.findByTokenHashForUpdate(Tokens.sha256(raw))).thenReturn(Optional.of(current));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.Rotation rot = service().rotate(raw, client);

        assertEquals(userId, rot.userId());
        assertNotEquals(raw, rot.rawToken(), "rotation must issue a brand-new token");
        assertTrue(current.isRevoked(), "old token must be revoked on rotation");
        verify(repo, times(2)).save(any()); // new token + updated old token
    }

    @Test
    void rotateOfRevokedTokenIsTreatedAsReuseAndRevokesAll() {
        UUID userId = UUID.randomUUID();
        String raw = Tokens.generate();
        RefreshToken revoked = token(userId, raw, Instant.now().plusSeconds(1000));
        revoked.revoke();
        when(repo.findByTokenHashForUpdate(Tokens.sha256(raw))).thenReturn(Optional.of(revoked));

        ApiException ex = assertThrows(ApiException.class, () -> service().rotate(raw, client));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(repo).revokeAllForUser(userId);
    }

    @Test
    void rotateOfExpiredTokenThrows() {
        String raw = Tokens.generate();
        RefreshToken expired = token(UUID.randomUUID(), raw, Instant.now().minusSeconds(10));
        when(repo.findByTokenHashForUpdate(Tokens.sha256(raw))).thenReturn(Optional.of(expired));

        assertThrows(ApiException.class, () -> service().rotate(raw, client));
    }

    @Test
    void rotateOfUnknownTokenThrows() {
        when(repo.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service().rotate("nonexistent", client));
    }
}

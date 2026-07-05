package app.collide.control.auth;

import app.collide.control.common.ApiException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Verifies Google ID tokens for "Continue with Google". The Google client library
 * checks the signature against Google's rotating public keys AND validates the audience
 * (our client id), issuer and expiry — so we trust ONLY the verified token, never any
 * profile fields the frontend might send.
 *
 * If no client id is configured, Google login is disabled and calls fail with 503.
 */
@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    private final GoogleIdTokenVerifier verifier;
    private final boolean enabled;

    public GoogleTokenVerifier(@Value("${collide.google.client-id}") String clientId) {
        this.enabled = clientId != null && !clientId.isBlank();
        this.verifier = enabled
                ? new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(clientId))
                        .build()
                : null;
        if (!enabled) {
            log.info("Google login disabled (collide.google.client-id not set)");
        }
    }

    /** Verified Google identity. `subject` is Google's stable user id (the `sub` claim). */
    public record GoogleUser(String subject, String email, String name, String picture) {}

    /**
     * Verify a raw ID token. Throws 401 for an invalid/expired/wrong-audience token,
     * 503 if Google can't be reached or the feature is disabled.
     */
    public GoogleUser verify(String idTokenString) {
        if (!enabled) {
            throw ApiException.serviceUnavailable("Google login is not configured");
        }
        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (GeneralSecurityException | IOException e) {
            // Network failure / Google unavailable / clock issues fetching certs.
            log.warn("Google token verification failed to reach Google: {}", e.getMessage());
            throw ApiException.serviceUnavailable("unable to verify Google token, try again");
        }
        if (idToken == null) {
            throw ApiException.unauthorized("invalid Google token");
        }
        GoogleIdToken.Payload p = idToken.getPayload();
        if (!Boolean.TRUE.equals(p.getEmailVerified())) {
            throw ApiException.unauthorized("Google email is not verified");
        }
        return new GoogleUser(
                p.getSubject(),
                p.getEmail(),
                (String) p.get("name"),
                (String) p.get("picture"));
    }
}

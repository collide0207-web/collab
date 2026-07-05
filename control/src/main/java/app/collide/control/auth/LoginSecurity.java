package app.collide.control.auth;

import app.collide.control.audit.LoginHistory;
import app.collide.control.audit.LoginHistoryRepository;
import app.collide.control.common.ClientInfo;
import app.collide.control.user.User;
import app.collide.control.user.UserRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records login outcomes and drives failed-login lockout. These side effects run in
 * their OWN transaction (REQUIRES_NEW) so they COMMIT even though the login path throws
 * a 401/403 afterwards — otherwise the audit row and the incremented failure counter
 * would be rolled back with the request. The failure path takes a pessimistic row lock
 * so concurrent failed attempts for one account can't lose an increment (lost update).
 */
@Component
public class LoginSecurity {

    private final UserRepository users;
    private final LoginHistoryRepository history;
    private final int maxFailures;
    private final long lockSeconds;

    public LoginSecurity(
            UserRepository users,
            LoginHistoryRepository history,
            @Value("${collide.security.lockout.max-failures}") int maxFailures,
            @Value("${collide.security.lockout.duration-seconds}") long lockSeconds) {
        this.users = users;
        this.history = history;
        this.maxFailures = maxFailures;
        this.lockSeconds = lockSeconds;
    }

    /** A bad-credentials failure: increment the counter (locking on threshold) and audit. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void badCredentials(UUID userId, String email, ClientInfo c, String reason) {
        if (userId != null) {
            users.findByEmailForUpdate(email)
                    .ifPresent(u -> u.onLoginFailure(maxFailures, lockSeconds));
        }
        history.save(LoginHistory.failure(userId, email, c.ip(), c.userAgent(), reason));
    }

    /** A non-credential failure (locked/disabled): audit only, don't touch the counter. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(UUID userId, String email, ClientInfo c, String reason) {
        history.save(LoginHistory.failure(userId, email, c.ip(), c.userAgent(), reason));
    }

    /**
     * A successful login/signup: reset the counter + stamp last-login, and audit.
     * JOINS the caller's transaction (default propagation) so that on signup — where the
     * user row isn't committed yet — the audit's FK to users is satisfied and everything
     * commits atomically.
     */
    @Transactional
    public void succeeded(UUID userId, String email, ClientInfo c) {
        users.findById(userId).ifPresent(User::onLoginSuccess);
        history.save(LoginHistory.success(userId, email, c.ip(), c.userAgent()));
    }
}

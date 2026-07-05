package app.collide.control.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit row for a single login attempt (success or failure). Used for
 * intrusion detection, "recent sign-ins", and forensic review. `userId` is nullable:
 * a failed attempt for an unknown/mistyped email has no user to attribute it to.
 * We record IP + user-agent but never any credential material.
 */
@Entity
@Table(name = "login_history")
public class LoginHistory {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String email;

    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "login_time", nullable = false)
    private Instant loginTime = Instant.now();

    @Column(nullable = false)
    private boolean success;

    @Column(name = "failure_reason")
    private String failureReason;

    protected LoginHistory() {}

    private LoginHistory(UUID userId, String email, String ip, String userAgent,
                         boolean success, String failureReason) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.email = email;
        this.ip = ip;
        this.userAgent = userAgent;
        this.success = success;
        this.failureReason = failureReason;
        this.loginTime = Instant.now();
    }

    public static LoginHistory success(UUID userId, String email, String ip, String userAgent) {
        return new LoginHistory(userId, email, ip, userAgent, true, null);
    }

    public static LoginHistory failure(UUID userId, String email, String ip, String userAgent, String reason) {
        return new LoginHistory(userId, email, ip, userAgent, false, reason);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public Instant getLoginTime() { return loginTime; }
    public boolean isSuccess() { return success; }
    public String getFailureReason() { return failureReason; }
}

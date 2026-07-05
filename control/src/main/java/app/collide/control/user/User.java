package app.collide.control.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Application user. One row per identity. Supports LOCAL (email+password) and GOOGLE
 * accounts. State transitions (login success/failure, lockout, soft-delete, password
 * change) are expressed as intention-revealing methods rather than open setters, so
 * invariants live on the entity — not scattered across services.
 *
 * `passwordHash` is nullable: a GOOGLE-only account has no local password.
 * `version` drives Hibernate optimistic locking, preventing lost updates under the
 * concurrent refresh/login/profile edits the spec calls out.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    /** Display name (may be non-unique). Distinct from the unique login `username`. */
    @Column(nullable = false)
    private String name;

    /** Null for GOOGLE-only accounts. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "google_id")
    private String googleId;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "profile_picture")
    private String profilePicture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountRole role = AccountRole.USER;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    protected User() {}

    private User(UUID id, String email, String username, String name, AuthProvider provider) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.name = name;
        this.authProvider = provider;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Factory for a LOCAL (email+password) account. Password is already hashed. */
    public static User local(UUID id, String email, String username, String name, String passwordHash) {
        User u = new User(id, email, username, name, AuthProvider.LOCAL);
        u.passwordHash = passwordHash;
        u.emailVerified = false;
        return u;
    }

    /** Factory for a GOOGLE account. Email is verified by Google, so trusted here. */
    public static User google(UUID id, String email, String username, String name,
                              String googleId, String profilePicture) {
        User u = new User(id, email, username, name, AuthProvider.GOOGLE);
        u.googleId = googleId;
        u.profilePicture = profilePicture;
        u.emailVerified = true;
        return u;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    // --- domain behaviour -------------------------------------------------

    /** True while a brute-force lockout window is still in effect. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /** Record a successful login: reset the failure counter and stamp last-login. */
    public void onLoginSuccess() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    /**
     * Record a failed login. Once failures reach {@code maxFailures}, lock the account
     * for {@code lockSeconds} and reset the counter so the next window starts clean.
     */
    public void onLoginFailure(int maxFailures, long lockSeconds) {
        this.failedLoginCount += 1;
        if (this.failedLoginCount >= maxFailures) {
            this.lockedUntil = Instant.now().plusSeconds(lockSeconds);
            this.failedLoginCount = 0;
        }
    }

    /** Link a Google identity to an existing LOCAL account (account linking). */
    public void linkGoogle(String googleId, String profilePicture) {
        this.googleId = googleId;
        if (this.profilePicture == null) this.profilePicture = profilePicture;
        this.emailVerified = true;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void setProfilePicture(String url) {
        this.profilePicture = url;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    /** Soft delete: retain the row for audit/foreign keys but disable the account. */
    public void softDelete() {
        this.deleted = true;
        this.active = false;
    }

    // --- getters ----------------------------------------------------------

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getPasswordHash() { return passwordHash; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public String getGoogleId() { return googleId; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getProfilePicture() { return profilePicture; }
    public AccountRole getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isDeleted() { return deleted; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}

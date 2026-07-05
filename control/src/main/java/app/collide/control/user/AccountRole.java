package app.collide.control.user;

/**
 * Account-level (global) role, distinct from the per-room {@code common.Role}
 * (OWNER/EDITOR/VIEWER). Carried in the JWT {@code roles} claim and mapped to a
 * Spring authority {@code ROLE_<name>}. USER/ADMIN are used today; MODERATOR/OWNER
 * are defined for forward-compatibility so adding them needs no schema change.
 */
public enum AccountRole {
    USER,
    ADMIN,
    MODERATOR,
    OWNER;

    /** Spring Security authority name, e.g. ROLE_ADMIN. */
    public String authority() {
        return "ROLE_" + name();
    }
}

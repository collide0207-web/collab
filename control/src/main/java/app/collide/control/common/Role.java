package app.collide.control.common;

public enum Role {
    OWNER,
    EDITOR,
    VIEWER;

    public static Role fromString(String s) {
        return Role.valueOf(s.toUpperCase());
    }

    public String wire() {
        return name().toLowerCase();
    }

    public boolean canWrite() {
        return this == OWNER || this == EDITOR;
    }
}

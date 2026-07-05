package app.collide.control.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Exhaustively checks the password policy (length + character-class requirements). */
class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    private boolean ok(String password) {
        return validator.isValid(password, null);
    }

    @Test
    void acceptsAStrongPassword() {
        assertTrue(ok("Abcdef1!"));
        assertTrue(ok("Str0ng&Pass"));
    }

    @Test
    void rejectsTooShort() {
        assertFalse(ok("Ab1!"));
    }

    @Test
    void rejectsWithoutUppercase() {
        assertFalse(ok("abcdef1!"));
    }

    @Test
    void rejectsWithoutLowercase() {
        assertFalse(ok("ABCDEF1!"));
    }

    @Test
    void rejectsWithoutDigit() {
        assertFalse(ok("Abcdefg!"));
    }

    @Test
    void rejectsWithoutSpecialCharacter() {
        assertFalse(ok("Abcdefg1"));
    }

    @Test
    void rejectsNull() {
        assertFalse(ok(null));
    }

    @Test
    void rejectsExcessivelyLong() {
        assertFalse(ok("Aa1!".repeat(40))); // 160 chars > 128 cap
    }
}

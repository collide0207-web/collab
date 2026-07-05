package app.collide.control.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN = 8;
    private static final int MAX = 128; // BCrypt only uses the first 72 bytes; cap input to bound work.

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return false;
        int len = value.length();
        if (len < MIN || len > MAX) return false;

        boolean upper = false, lower = false, digit = false, special = false;
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
            else special = true; // anything not alphanumeric counts as special
        }
        return upper && lower && digit && special;
    }
}

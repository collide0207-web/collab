package app.collide.control.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bean-validation constraint enforcing the password policy: 8+ chars with an
 * uppercase, a lowercase, a digit and a special character (and an upper bound so a
 * multi-megabyte password can't be used to burn CPU in BCrypt).
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default
            "password must be 8-128 chars and include an uppercase, lowercase, digit and special character";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

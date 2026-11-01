package com.docket.app.security;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

// Password rules for new passwords. Keep in step with passwordSchema in the
// frontend's lib/validation.ts. The upper limit is bytes because bcrypt can't take
// more than 72, and non-Latin characters take several bytes each.
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
public @interface ValidPassword {
    String message() default "Password must be 8 to 72 bytes long and contain a letter and a number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPassword, String> {
        @Override
        public boolean isValid(String password, ConstraintValidatorContext context) {
            if (password == null) {
                return false;
            }
            return password.length() >= 8
                    && password.getBytes(StandardCharsets.UTF_8).length <= 72
                    && password.chars().anyMatch(Character::isLetter)
                    && password.chars().anyMatch(Character::isDigit);
        }
    }
}

package com.spatialstay.user;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    public static void validate(String password) {

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "Password is required."
            );
        }

        if (password.length() < MIN_LENGTH
                || password.length() > MAX_LENGTH) {

            throw new IllegalArgumentException(
                    "Password must contain between "
                            + MIN_LENGTH
                            + " and "
                            + MAX_LENGTH
                            + " characters."
            );
        }

        boolean hasLetter = false;
        boolean hasDigit = false;

        for (char character : password.toCharArray()) {

            if (Character.isLetter(character)) {
                hasLetter = true;
            }

            if (Character.isDigit(character)) {
                hasDigit = true;
            }

            if (hasLetter && hasDigit) {
                return;
            }
        }

        throw new IllegalArgumentException(
                "Password must contain at least one letter and one number."
        );
    }
}
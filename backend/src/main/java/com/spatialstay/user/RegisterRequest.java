package com.spatialstay.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "First name is required.")
        @Size(max = 100, message = "First name must not exceed 100 characters.")
        String firstName,

        @NotBlank(message = "Last name is required.")
        @Size(max = 100, message = "Last name must not exceed 100 characters.")
        String lastName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Email address is too long.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(
                min = 10,
                max = 128,
                message = "Password must contain between 10 and 128 characters."
        )
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one number."
        )
        String password

) {
}
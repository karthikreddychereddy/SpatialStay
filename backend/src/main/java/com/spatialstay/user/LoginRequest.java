package com.spatialstay.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 320, message = "Email address is too long.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 128, message = "Password is too long.")
        String password

) {
}
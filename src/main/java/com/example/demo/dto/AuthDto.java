package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDto {

    @Data
    public static class SignupRequest {
        private String email;
        private String password;
        private String name;
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenResponse {
        private String token;
        private String userId;
        private String email;
        private String name;
    }
}

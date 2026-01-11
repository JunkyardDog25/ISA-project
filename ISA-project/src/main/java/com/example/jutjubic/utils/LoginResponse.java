package com.example.jutjubic.utils;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter @Setter
public class LoginResponse {
    private String token;
    private long expiresIn;
    private UUID id;
    private String username;
    private String email;

    public LoginResponse(String token, long expiresIn, UUID id, String username, String email) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.id = id;
        this.username = username;
        this.email = email;
    }
}

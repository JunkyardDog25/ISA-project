package com.example.jutjubic.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO for {@link com.example.jutjubic.models.User}
 */
@Getter @Setter
public class LoginUserDto {
    private String email;
    private String password;
}

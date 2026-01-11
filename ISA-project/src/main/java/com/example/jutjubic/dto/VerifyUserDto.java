package com.example.jutjubic.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO for {@link com.example.jutjubic.models.User}
 */
@Getter @Setter
public class VerifyUserDto {
    private String email;
    private String verificationCode;
}

package com.example.jutjubic.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO for {@link com.example.jutjubic.models.User}
 */
@Getter @Setter
public class RegisterUserDto {
    String username;
    String password;
    String email;
    String firstName;
    String lastName;
    String address;
}
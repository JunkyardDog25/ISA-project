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
    // optional location as "lat,lon" (e.g. "45.8150,15.9819")
    private String location;
}

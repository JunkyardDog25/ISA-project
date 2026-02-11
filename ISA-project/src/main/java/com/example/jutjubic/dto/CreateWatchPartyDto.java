package com.example.jutjubic.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO za kreiranje Watch Party sobe.
 */
@Getter @Setter
public class CreateWatchPartyDto {

    private String name;
    private String description;
    private boolean isPublic = false;

    public CreateWatchPartyDto() {
    }

    public CreateWatchPartyDto(String name, String description, boolean isPublic) {
        this.name = name;
        this.description = description;
        this.isPublic = isPublic;
    }
}


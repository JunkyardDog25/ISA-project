package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter @Setter
public class ViewResponseDto {
    private boolean viewed;
    private long views;
}

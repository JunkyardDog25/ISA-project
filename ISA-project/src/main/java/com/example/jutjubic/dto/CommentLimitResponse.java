package com.example.jutjubic.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO za response koji sadrži informacije o ograničenju komentara.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CommentLimitResponse {
    private int limit;
    private int used;
    private int remaining;
    private long resetInMinutes;

    /**
     * Kreira response na osnovu trenutnog stanja.
     */
    public static CommentLimitResponse of(int limit, int used) {
        return new CommentLimitResponse(
            limit,
            used,
            Math.max(0, limit - used),
            60 // Reset je uvek za sat vremena od prvog komentara
        );
    }
}


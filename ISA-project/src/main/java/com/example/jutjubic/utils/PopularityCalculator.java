package com.example.jutjubic.utils;

import com.example.jutjubic.models.VideoView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class PopularityCalculator {
    private PopularityCalculator() {}

    /**
     * Compute the popularity score for a single VideoView event.
     * Only views that happened in the last 7 days are counted.
     * Weight = 8 - daysAgo.
     *
     * @param event VideoView event
     * @return popularity score (double)
     */
    public static Double calculateScore(VideoView event) {
        if (event == null || event.getVideo() == null || event.getCreatedAt() == null) {
            return 0.0;
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate viewDate = event.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();

        long daysAgo = ChronoUnit.DAYS.between(viewDate, today);

        // Only consider views from 1..7 days ago
        if (daysAgo >= 1 && daysAgo <= 7) {
            long score = event.getVideo().getViewCount() * (8 - daysAgo);
            return (double) score;
        }

        return 0.0;
    }
}

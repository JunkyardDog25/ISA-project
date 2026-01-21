package com.example.jutjubic.repositories;

import com.example.jutjubic.models.DailyPopularVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DailyPopularVideoRepository extends JpaRepository<DailyPopularVideo, UUID> {
    @Modifying
    @Transactional
    @Query(value = "WITH max_per_video AS (\n" +
            "    SELECT video_id, MAX(popularity_score) AS max_score\n" +
            "    FROM daily_popular_videos\n" +
            "    GROUP BY video_id\n" +
            "),\n" +
            "     top_videos AS (\n" +
            "         SELECT video_id\n" +
            "         FROM max_per_video\n" +
            "         ORDER BY max_score DESC\n" +
            "         LIMIT 3\n" +
            "     ),\n" +
            "     rows_to_keep AS (\n" +
            "         SELECT id FROM (\n" +
            "                            SELECT dp.id,\n" +
            "                                   ROW_NUMBER() OVER (PARTITION BY dp.video_id ORDER BY dp.popularity_score DESC, dp.id) AS rn\n" +
            "                            FROM daily_popular_videos dp\n" +
            "                            WHERE dp.video_id IN (SELECT video_id FROM top_videos)\n" +
            "                        ) t\n" +
            "         WHERE rn = 1\n" +
            "     )\n" +
            "DELETE FROM daily_popular_videos\n" +
            "WHERE id NOT IN (SELECT id FROM rows_to_keep)",
            nativeQuery = true)
    void keepOnlyTop3();
}
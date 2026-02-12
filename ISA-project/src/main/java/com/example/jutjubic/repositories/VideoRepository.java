package com.example.jutjubic.repositories;

import com.example.jutjubic.models.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    Optional<Video> findVideoById(UUID id);

    /**
     * Pronalazi sve video objave za datog korisnika sa paginacijom.
     */
    Page<Video> findByCreatorId(UUID creatorId, Pageable pageable);

    /**
     * Pronalazi sve javno dostupne video objave sa paginacijom.
     * Video je javno dostupan ako:
     * - scheduledAt je NULL (nije zakazan) ili
     * - scheduledAt je manji ili jednak trenutnom vremenu (zakazani datum je prošao)
     *
     * @param now Trenutno vreme
     * @param pageable Parametri paginacije
     * @return Stranica javno dostupnih video objava
     */
    @Query("SELECT v FROM Video v WHERE v.scheduledAt IS NULL OR v.scheduledAt <= :now")
    Page<Video> findAllPubliclyAvailable(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * Pronalazi sve javno dostupne video objave bez paginacije.
     *
     * @param now Trenutno vreme
     * @return Lista javno dostupnih video objava
     */
    @Query("SELECT v FROM Video v WHERE v.scheduledAt IS NULL OR v.scheduledAt <= :now ORDER BY v.createdAt DESC")
    List<Video> findAllPubliclyAvailable(@Param("now") LocalDateTime now);

    /**
     * Finds videos within a specified radius from a center point using spatial indexing.
     *
     * <h3>Spatial Indexing Strategy:</h3>
     * <p>This query uses a two-phase approach for efficient spatial search:</p>
     * <ol>
     *   <li><b>Phase 1 - Bounding Box Pre-filter (Index Scan):</b>
     *       Uses B-Tree indexes on latitude and longitude columns to quickly filter
     *       videos within a rectangular bounding box. This dramatically reduces the
     *       dataset before applying the expensive distance calculation.</li>
     *   <li><b>Phase 2 - Haversine Refinement:</b>
     *       Applies the Haversine formula only to pre-filtered results to get
     *       exact circular distance, eliminating false positives from box corners.</li>
     * </ol>
     *
     * <h3>Required Indexes (see spatial_indexes.sql):</h3>
     * <ul>
     *   <li>idx_videos_latitude - B-Tree index on latitude column</li>
     *   <li>idx_videos_longitude - B-Tree index on longitude column</li>
     *   <li>idx_videos_lat_lon - Composite index on (latitude, longitude)</li>
     * </ul>
     *
     * <h3>Performance:</h3>
     * <p>The bounding box prefilter reduces full table scans to index range scans,
     * improving query performance from O(n) to O(log n + k) where k is the number
     * of results within the bounding box.</p>
     *
     * @param minLat Minimum latitude of bounding box
     * @param maxLat Maximum latitude of bounding box
     * @param minLon Minimum longitude of bounding box
     * @param maxLon Maximum longitude of bounding box
     * @param lat Center point latitude for Haversine calculation
     * @param lon Center point longitude for Haversine calculation
     * @param radiusMeters Search radius in meters
     * @param pageable Pagination parameters
     * @return Page of videos within the specified radius
     *
     * @see <a href="https://www.geeksforgeeks.org/dsa/understanding-efficient-spatial-indexing/">
     *      Understanding Efficient Spatial Indexing</a>
     */
    @Query(value = "SELECT * FROM videos v " +
            "WHERE v.latitude BETWEEN :minLat AND :maxLat " +
            "AND v.longitude BETWEEN :minLon AND :maxLon " +
            "AND (v.scheduled_at IS NULL OR v.scheduled_at <= :now) " +
            "AND (6371000 * ACOS( " +
            "    COS(RADIANS(:lat)) * COS(RADIANS(v.latitude)) * " +
            "    COS(RADIANS(v.longitude) - RADIANS(:lon)) + " +
            "    SIN(RADIANS(:lat)) * SIN(RADIANS(v.latitude)) " +
            ")) <= :radiusMeters",
            countQuery = "SELECT count(*) FROM videos v " +
                    "WHERE v.latitude BETWEEN :minLat AND :maxLat " +
                    "AND v.longitude BETWEEN :minLon AND :maxLon " +
                    "AND (v.scheduled_at IS NULL OR v.scheduled_at <= :now) " +
                    "AND (6371000 * ACOS( " +
                    "    COS(RADIANS(:lat)) * COS(RADIANS(v.latitude)) * " +
                    "    COS(RADIANS(v.longitude) - RADIANS(:lon)) + " +
                    "    SIN(RADIANS(:lat)) * SIN(RADIANS(v.latitude)) " +
                    ")) <= :radiusMeters",
            nativeQuery = true)
    Page<Video> findNearby(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") double radiusMeters,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    /**
     * Pronalazi sve video objave sa nekompresovanim thumbnail-ima koje su starije od određenog datuma.
     * Video ima nekompresovan thumbnail ako:
     * - thumbnailPath nije NULL (ima original thumbnail)
     * - thumbnailCompressedPath je NULL (nema kompresovanu verziju)
     * - createdAt je manji od dateCutoff (starije od mesec dana)
     */
    @Query("SELECT v FROM Video v WHERE v.thumbnailPath IS NOT NULL AND v.thumbnailCompressedPath IS NULL AND v.createdAt < :dateCutoff")
    List<Video> findVideosWithUncompressedThumbnails(@Param("dateCutoff") LocalDateTime dateCutoff);

    /**
     * Pronalazi SVE video objave sa nekompresovanim thumbnail-ima (bez obzira na starost).
     * Koristi se za manuelnu kompresiju svih thumbnail-a.
     */
    @Query("SELECT v FROM Video v WHERE v.thumbnailPath IS NOT NULL AND v.thumbnailCompressedPath IS NULL")
    List<Video> findAllVideosWithUncompressedThumbnails();
}
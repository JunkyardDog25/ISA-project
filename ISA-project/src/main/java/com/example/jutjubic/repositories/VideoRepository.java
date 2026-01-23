package com.example.jutjubic.repositories;

import com.example.jutjubic.models.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    Optional<Video> findVideoById(UUID id);

    /**
     * Pronalazi sve video objave za datog korisnika sa paginacijom.
     */
    Page<Video> findByCreatorId(UUID creatorId, Pageable pageable);

    /**
     * Finds videos inside a bounding box and within the Haversine distance (meters) of the center point.
     * The bounding box prefilter (latitude/longitude ranges) allows use of indexes on those numeric columns.
     */
    @Query(value = "SELECT * FROM videos v WHERE v.latitude BETWEEN :minLat AND :maxLat AND v.longitude BETWEEN :minLon AND :maxLon AND (6371000 * ACOS( COS(RADIANS(:lat)) * COS(RADIANS(v.latitude)) * COS(RADIANS(v.longitude) - RADIANS(:lon)) + SIN(RADIANS(:lat)) * SIN(RADIANS(v.latitude)) )) <= :radiusMeters",
            countQuery = "SELECT count(*) FROM videos v WHERE v.latitude BETWEEN :minLat AND :maxLat AND v.longitude BETWEEN :minLon AND :maxLon AND (6371000 * ACOS( COS(RADIANS(:lat)) * COS(RADIANS(v.latitude)) * COS(RADIANS(v.longitude) - RADIANS(:lon)) + SIN(RADIANS(:lat)) * SIN(RADIANS(v.latitude)) )) <= :radiusMeters",
            nativeQuery = true)
    Page<Video> findNearby(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon,
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radiusMeters") double radiusMeters,
            Pageable pageable
    );
}
-- ============================================================================
-- SPATIAL INDEXING FOR NEARBY VIDEO SEARCH
-- ============================================================================
-- This migration adds spatial indexes to optimize geographic queries.
--
-- Strategy: Bounding Box Pre-filtering with B-Tree Indexes
-- ---------------------------------------------------------
-- MySQL's standard B-Tree indexes on latitude and longitude columns allow
-- efficient range queries for bounding box filtering. The Haversine formula
-- is then applied only to the pre-filtered results for exact distance calculation.
--
-- This approach is recommended for:
-- - Datasets where most queries use a similar search radius
-- - Applications that don't require complex spatial operations (intersect, contains, etc.)
-- - MySQL versions where SPATIAL INDEX on separate lat/lon columns isn't optimal
--
-- Reference: https://www.geeksforgeeks.org/dsa/understanding-efficient-spatial-indexing/
-- ============================================================================

-- Add index on latitude column for bounding box north-south filtering
CREATE INDEX IF NOT EXISTS idx_videos_latitude ON videos (latitude);

-- Add index on longitude column for bounding box east-west filtering
CREATE INDEX IF NOT EXISTS idx_videos_longitude ON videos (longitude);

-- Composite index for combined lat/lon queries (covers both columns in one index scan)
-- This is particularly efficient for bounding box queries that filter on both columns
CREATE INDEX IF NOT EXISTS idx_videos_lat_lon ON videos (latitude, longitude);

-- ============================================================================
-- ALTERNATIVE: MySQL SPATIAL INDEX (Point geometry)
-- ============================================================================
-- If you need more advanced spatial operations, you can use MySQL's native
-- spatial indexing with a POINT column. Uncomment below if needed:
--
-- ALTER TABLE videos ADD COLUMN location POINT;
-- UPDATE videos SET location = ST_SRID(POINT(longitude, latitude), 4326)
--     WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
-- CREATE SPATIAL INDEX idx_videos_location ON videos (location);
--
-- Query example with spatial index:
-- SELECT * FROM videos
-- WHERE ST_Distance_Sphere(location, ST_SRID(POINT(:lon, :lat), 4326)) <= :radiusMeters;
-- ============================================================================

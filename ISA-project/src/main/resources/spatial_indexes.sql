-- ============================================================================
-- SPATIAL INDEXING FOR NEARBY VIDEO SEARCH
-- Run this script manually on your MySQL database to add spatial indexes
-- ============================================================================
--
-- Strategy: Bounding Box Pre-filtering with B-Tree Indexes
-- ---------------------------------------------------------
-- MySQL's standard B-Tree indexes on latitude and longitude columns allow
-- efficient range queries for bounding box filtering. The Haversine formula
-- is then applied only to the pre-filtered results for exact distance calculation.
--
-- This is a two-phase spatial query optimization:
-- 1. Phase 1 (Index Scan): Use B-Tree indexes to quickly filter videos within
--    a rectangular bounding box (minLat/maxLat, minLon/maxLon)
-- 2. Phase 2 (Refinement): Apply Haversine formula only to the filtered results
--    to get exact circular distance
--
-- Reference: https://www.geeksforgeeks.org/dsa/understanding-efficient-spatial-indexing/
-- ============================================================================

-- Check if indexes exist and add them if not
-- Note: MySQL will use these indexes for the bounding box range queries in findNearby()

-- Index on latitude for north-south bounding box filtering
-- Speeds up: WHERE latitude BETWEEN :minLat AND :maxLat
ALTER TABLE videos ADD INDEX idx_videos_latitude (latitude);

-- Index on longitude for east-west bounding box filtering
-- Speeds up: WHERE longitude BETWEEN :minLon AND :maxLon
ALTER TABLE videos ADD INDEX idx_videos_longitude (longitude);

-- Composite index for combined lat/lon queries
-- Most efficient for: WHERE latitude BETWEEN ... AND longitude BETWEEN ...
-- MySQL can use this single index to satisfy both range conditions
ALTER TABLE videos ADD INDEX idx_videos_lat_lon (latitude, longitude);

-- ============================================================================
-- VERIFY INDEXES WERE CREATED
-- ============================================================================
SHOW INDEX FROM videos WHERE Key_name LIKE 'idx_videos%';

-- ============================================================================
-- QUERY EXECUTION PLAN (for verification)
-- ============================================================================
-- Run this EXPLAIN to verify the index is being used:
--
-- EXPLAIN SELECT * FROM videos v
-- WHERE v.latitude BETWEEN 44.0 AND 45.0
--   AND v.longitude BETWEEN 20.0 AND 21.0
--   AND (6371000 * ACOS(COS(RADIANS(44.5)) * COS(RADIANS(v.latitude))
--        * COS(RADIANS(v.longitude) - RADIANS(20.5))
--        + SIN(RADIANS(44.5)) * SIN(RADIANS(v.latitude)))) <= 5000;
--
-- Expected: "Using index" or "range" in the Extra/type column
-- ============================================================================

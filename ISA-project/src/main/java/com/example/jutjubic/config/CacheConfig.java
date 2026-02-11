package com.example.jutjubic.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application caching.
 * Uses ConcurrentMapCacheManager as an in-memory cache provider.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String COMMENTS_CACHE = "commentsCache";
    public static final String COMMENT_COUNT_CACHE = "commentCountCache";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(COMMENTS_CACHE, COMMENT_COUNT_CACHE);
    }
}


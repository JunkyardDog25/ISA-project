package com.example.jutjubic.config;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String MEDIA_DIR = "media";
    private static final String VIDEOS_DIR = "videos";
    private static final String THUMBNAILS_DIR = "thumbnails";
    private static final int CACHE_PERIOD_SECONDS = 3600;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registerResourceHandler(registry, "/media/**", MEDIA_DIR);
        registerResourceHandler(registry, "/videos/**", MEDIA_DIR, VIDEOS_DIR);
        registerResourceHandler(registry, "/thumbnails/**", MEDIA_DIR, THUMBNAILS_DIR);
    }

    private void registerResourceHandler(ResourceHandlerRegistry registry, String pathPattern, String... directories) {
        Path resourceDir = Paths.get(directories[0], java.util.Arrays.copyOfRange(directories, 1, directories.length)).toAbsolutePath();
        String resourcePath = resourceDir.toUri().toString();

        registry.addResourceHandler(pathPattern)
                .addResourceLocations(resourcePath)
                .setCachePeriod(CACHE_PERIOD_SECONDS);
    }
}

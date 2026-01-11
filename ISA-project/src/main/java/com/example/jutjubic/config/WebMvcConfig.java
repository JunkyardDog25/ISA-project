package com.example.jutjubic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve files from the filesystem 'media/' directory at /media/**
        Path mediaDir = Paths.get("media").toAbsolutePath();
        String mediaPath = mediaDir.toUri().toString();

        registry.addResourceHandler("/media/**")
                .addResourceLocations(mediaPath)
                .setCachePeriod(3600);

        // Also serve videos directly at /videos/** (for backward compatibility)
        Path videosDir = Paths.get("media", "videos").toAbsolutePath();
        String videosPath = videosDir.toUri().toString();
        registry.addResourceHandler("/videos/**")
                .addResourceLocations(videosPath)
                .setCachePeriod(3600);

        // Also serve thumbnails directly at /thumbnails/** (for backward compatibility)
        Path thumbsDir = Paths.get("media", "thumbnails").toAbsolutePath();
        String thumbsPath = thumbsDir.toUri().toString();
        registry.addResourceHandler("/thumbnails/**")
                .addResourceLocations(thumbsPath)
                .setCachePeriod(3600);
    }
}

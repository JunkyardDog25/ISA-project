package com.example.jutjubic.services;

import com.example.jutjubic.models.DailyPopularVideo;
import com.example.jutjubic.repositories.DailyPopularVideoRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class DailyPopularVideosService {
    private final DailyPopularVideoRepository dailyPopularVideoRepository;

    public List<DailyPopularVideo> getAll() {
        return dailyPopularVideoRepository.findAll();
    }

    public DailyPopularVideosService(DailyPopularVideoRepository dailyPopularVideoRepository) {
        this.dailyPopularVideoRepository = dailyPopularVideoRepository;
    }
}

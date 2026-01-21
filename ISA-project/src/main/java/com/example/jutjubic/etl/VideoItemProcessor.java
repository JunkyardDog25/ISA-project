package com.example.jutjubic.etl;

import com.example.jutjubic.models.Video;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class VideoItemProcessor implements ItemProcessor<Video, Video> {

    @Override
    public Video process(Video video) {
        return video;
    }

}

package com.example.jutjubic.services;

import com.example.jutjubic.dto.CommentDto;
import com.example.jutjubic.models.Comment;
import com.example.jutjubic.models.User;
import com.example.jutjubic.models.Video;
import com.example.jutjubic.repositories.CommentRepository;
import com.example.jutjubic.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CommentService {
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final VideoService videoService;

    public CommentService(CommentRepository commentRepository, UserService userService, VideoService videoService) {
        this.commentRepository = commentRepository;
        this.userService = userService;
        this.videoService = videoService;
    }

    public List<CommentDto> getAllCommentsByVideoId(UUID videoId) {
        List<Comment> comments = commentRepository.findAllByVideo_Id(videoId);
        return comments.stream()
                .map(comment -> new CommentDto(
                        comment.getId(),
                        comment.getUser().getId(),
                        comment.getUser().getUsername(),
                        comment.getVideo().getId(),
                        comment.getContent(),
                        comment.getCreatedAt()
                ))
                .toList();
    }

    public Comment createComment(UUID videoId, CommentDto commentDto) {
        User user = userService.getUserById(commentDto.getUserId());
        Video video = videoService.getVideoById(videoId);

        Comment comment = new Comment(
                user,
                video,
                commentDto.getContent()
        );
        return commentRepository.save(comment);
    }
}

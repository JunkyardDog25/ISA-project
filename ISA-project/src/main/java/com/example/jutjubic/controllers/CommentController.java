package com.example.jutjubic.controllers;

import com.example.jutjubic.dto.CommentDto;
import com.example.jutjubic.models.Comment;
import com.example.jutjubic.services.CommentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/comments")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/video/{videoId}")
    public ResponseEntity<List<CommentDto>> getAllCommentsByVideoId(@PathVariable UUID videoId) {
        List<CommentDto> comments = commentService.getAllCommentsByVideoId(videoId);
        return ResponseEntity.ok(comments);
    }

    @PostMapping("/video/{videoId}")
    public ResponseEntity<Comment> createComment(@PathVariable UUID videoId, @RequestBody CommentDto commentDto) {
        Comment createdComment = commentService.createComment(videoId, commentDto);
        return ResponseEntity.ok(createdComment);
    }
}

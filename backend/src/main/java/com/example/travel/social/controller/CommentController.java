package com.example.travel.social.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.social.dto.CommentDtos;
import com.example.travel.social.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody CommentDtos.CreateRequest request) {
        commentService.create(request);
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<List<CommentDtos.CommentItem>> list(@RequestParam String targetType,
                                                           @RequestParam Long targetId) {
        return ApiResponse.success(commentService.list(targetType, targetId));
    }
}


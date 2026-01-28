package com.example.travel.social.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.social.dto.FeedDtos;
import com.example.travel.social.service.FeedService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feeds")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody FeedDtos.CreateRequest request) {
        return ApiResponse.success(feedService.create(request));
    }

    @GetMapping
    public ApiResponse<List<FeedDtos.FeedItem>> list() {
        return ApiResponse.success(feedService.list());
    }
}


package com.example.travel.companion.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.companion.dto.CompanionDtos;
import com.example.travel.companion.service.CompanionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companion")
public class CompanionController {

    private final CompanionService companionService;

    public CompanionController(CompanionService companionService) {
        this.companionService = companionService;
    }

    @PostMapping("/posts")
    public ApiResponse<Long> publish(@Valid @RequestBody CompanionDtos.PublishRequest request) {
        return ApiResponse.success(companionService.publish(request));
    }

    @GetMapping("/posts")
    public ApiResponse<List<CompanionDtos.PostSummary>> list(CompanionDtos.SearchRequest request) {
        return ApiResponse.success(companionService.search(request));
    }

    @GetMapping("/posts/my")
    public ApiResponse<List<CompanionDtos.PostSummary>> myPosts() {
        return ApiResponse.success(companionService.myPosts());
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<CompanionDtos.PostDetail> getPost(@PathVariable Long id) {
        return ApiResponse.success(companionService.getPostDetail(id));
    }

    @PostMapping("/teams")
    public ApiResponse<Long> createTeam(@RequestParam Long postId) {
        return ApiResponse.success(companionService.createTeam(postId));
    }

    @PostMapping("/teams/{id}/join")
    public ApiResponse<Void> joinTeam(@PathVariable Long id) {
        companionService.joinTeam(id);
        return ApiResponse.success();
    }

    @GetMapping("/teams/{id}")
    public ApiResponse<CompanionDtos.TeamDetail> getTeam(@PathVariable Long id) {
        return ApiResponse.success(companionService.getTeamDetail(id));
    }
}


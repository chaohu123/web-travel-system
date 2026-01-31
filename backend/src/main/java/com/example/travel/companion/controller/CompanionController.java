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

    @GetMapping("/posts/recommend")
    public ApiResponse<List<CompanionDtos.PostSummary>> recommend(@RequestParam(defaultValue = "3") int limit) {
        return ApiResponse.success(companionService.recommend(limit));
    }

    @GetMapping("/posts/my")
    public ApiResponse<List<CompanionDtos.PostSummary>> myPosts() {
        return ApiResponse.success(companionService.myPosts());
    }

    /** 当前用户加入的小队及每条小队对应结伴帖的最近聊天预览（消息中心「小队消息」） */
    @GetMapping("/me/teams")
    public ApiResponse<List<CompanionDtos.MyTeamMessageItem>> myTeamMessages() {
        return ApiResponse.success(companionService.myTeamMessages());
    }

    /** 获取结伴帖内置沟通消息列表（任何人可读）- 声明在 /posts/{id} 前避免被误匹配 */
    @GetMapping("/posts/{postId}/chat/messages")
    public ApiResponse<List<CompanionDtos.PostChatMessageItem>> getPostChatMessages(@PathVariable Long postId) {
        return ApiResponse.success(companionService.getPostChatMessages(postId));
    }

    /** 发送结伴帖内置沟通消息（需登录，且为发起人或已加入小队成员）- 声明在 /posts/{id} 前避免 404 */
    @PostMapping("/posts/{postId}/chat")
    public ApiResponse<CompanionDtos.PostChatMessageItem> sendPostChatMessage(
            @PathVariable Long postId,
            @Valid @RequestBody CompanionDtos.SendPostChatRequest request) {
        return ApiResponse.success(companionService.sendPostChatMessage(postId, request));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<CompanionDtos.PostDetail> getPost(@PathVariable Long id) {
        return ApiResponse.success(companionService.getPostDetail(id));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@PathVariable Long id) {
        companionService.deletePost(id);
        return ApiResponse.success();
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

    /** 退出小队（仅普通成员） */
    @PostMapping("/teams/{id}/quit")
    public ApiResponse<Void> quitTeam(@PathVariable Long id) {
        companionService.quitTeam(id);
        return ApiResponse.success();
    }

    /** 解散小队（仅队长） */
    @PostMapping("/teams/{id}/dissolve")
    public ApiResponse<Void> dissolveTeam(@PathVariable Long id) {
        companionService.dissolveTeam(id);
        return ApiResponse.success();
    }

    /** 队长移除指定成员 */
    @DeleteMapping("/teams/{teamId}/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long teamId, @PathVariable Long userId) {
        companionService.removeMember(teamId, userId);
        return ApiResponse.success();
    }

    /** 队长将小队分享给指定用户，被分享人可查看该行程 */
    @PostMapping("/teams/{teamId}/share")
    public ApiResponse<Void> shareTeam(@PathVariable Long teamId, @RequestParam Long userId) {
        companionService.shareTeam(teamId, userId);
        return ApiResponse.success();
    }

}


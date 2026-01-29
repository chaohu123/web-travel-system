package com.example.travel.social.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.social.dto.MessageDtos;
import com.example.travel.social.service.MessageService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 顶部概览：总未读数
     */
    @GetMapping("/overview")
    public ApiResponse<MessageDtos.Overview> overview() {
        return ApiResponse.success(messageService.overview());
    }

    /**
     * 互动消息列表（点赞/评论）
     */
    @GetMapping("/interactions")
    public ApiResponse<MessageDtos.PagedResult<MessageDtos.InteractionMessageItem>> listInteractions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "all") String category) {
        return ApiResponse.success(messageService.listInteractions(page, pageSize, category));
    }

    /**
     * 私信会话列表
     */
    @GetMapping("/conversations")
    public ApiResponse<MessageDtos.PagedResult<MessageDtos.ConversationSummary>> listConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(messageService.listConversations(page, pageSize));
    }

    /**
     * 标记单条互动消息已读
     */
    @PostMapping("/interactions/{id}/read")
    public ApiResponse<Void> markInteractionRead(@PathVariable Long id) {
        messageService.markInteractionRead(id);
        return ApiResponse.success();
    }

    /**
     * 将所有互动消息标记为已读
     */
    @PostMapping("/interactions/read-all")
    public ApiResponse<Void> markAllInteractionRead() {
        messageService.markAllInteractionRead();
        return ApiResponse.success();
    }

    /**
     * 清空某个会话的未读数
     */
    @PostMapping("/conversations/{id}/clear-unread")
    public ApiResponse<Void> clearConversationUnread(@PathVariable Long id) {
        messageService.clearConversationUnread(id);
        return ApiResponse.success();
    }
}


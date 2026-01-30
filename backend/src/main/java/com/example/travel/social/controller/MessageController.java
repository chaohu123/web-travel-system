package com.example.travel.social.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.social.dto.MessageDtos;
import com.example.travel.social.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * 发送私信给指定用户
     */
    @PostMapping("/chat/{peerUserId}")
    public ApiResponse<MessageDtos.ChatMessageItem> sendChatMessage(
            @PathVariable Long peerUserId,
            @Valid @RequestBody MessageDtos.SendChatRequest body) {
        return ApiResponse.success(messageService.sendChatMessage(peerUserId, body.getContent()));
    }

    /**
     * 获取与指定用户的私信消息列表（按时间正序）
     */
    @GetMapping("/chat/{peerUserId}/messages")
    public ApiResponse<List<MessageDtos.ChatMessageItem>> getChatMessages(@PathVariable Long peerUserId) {
        return ApiResponse.success(messageService.getChatMessagesWithPeer(peerUserId));
    }

    /**
     * 进入与指定用户的聊天页时清空该会话未读数
     */
    @PostMapping("/chat/{peerUserId}/clear-unread")
    public ApiResponse<Void> clearChatUnread(@PathVariable Long peerUserId) {
        messageService.clearConversationUnreadByPeer(peerUserId);
        return ApiResponse.success();
    }
}


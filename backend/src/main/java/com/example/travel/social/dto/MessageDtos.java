package com.example.travel.social.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MessageDtos {

    @Data
    public static class Overview {
        private Long totalUnread;
    }

    @Data
    public static class InteractionMessageItem {
        private Long id;
        private String type; // LIKE / COMMENT
        private Long fromUserId;
        private String fromUserName;
        private String fromUserAvatar;
        private String targetType; // note / route
        private Long targetId;
        private String targetTitle;
        private String contentPreview;
        private LocalDateTime createdAt;
        private Boolean read;
    }

    @Data
    public static class ConversationSummary {
        private Long id;
        private Long peerUserId;
        private String peerNickname;
        private String peerAvatar;
        private String lastMessagePreview;
        private LocalDateTime lastMessageTime;
        private Integer unreadCount;
        private Boolean pinned;
        /** 对方是否为当前用户的粉丝（对方关注了当前用户） */
        private Boolean peerIsFollower;
    }

    @Data
    public static class PagedResult<T> {
        private List<T> list;
        private Long total;
        private Integer page;
        private Integer pageSize;
    }

    /** 私信单条消息（用于聊天页拉取与展示） */
    @Data
    public static class ChatMessageItem {
        private Long id;
        private Long senderId;
        private String content;
        private String type; // text / image / route / companion / spot
        private String spotJson; // type=spot 时景点卡片数据 JSON
        private LocalDateTime createdAt;
    }

    /** 发送私信请求体 */
    @Data
    public static class SendChatRequest {
        /** 文本消息必填；图片消息时为 base64 或 URL；景点消息时为摘要文案 */
        private String content;
        /** 消息类型：text / image / route / companion / spot，默认 text */
        private String type;
        /** type=spot 时必填，景点数据 JSON（routeId, dayIndex, activityIndex, name, location） */
        private String spotJson;
    }
}


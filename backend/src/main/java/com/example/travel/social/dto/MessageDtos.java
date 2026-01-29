package com.example.travel.social.dto;

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
    }

    @Data
    public static class PagedResult<T> {
        private List<T> list;
        private Long total;
        private Integer page;
        private Integer pageSize;
    }
}


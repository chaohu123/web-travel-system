package com.example.travel.companion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CompanionDtos {

    @Data
    public static class PublishRequest {
        private Long relatedPlanId;

        @NotBlank
        private String destination;

        @NotNull
        private LocalDate startDate;

        @NotNull
        private LocalDate endDate;

        @Min(1)
        private Integer minPeople;

        @Min(1)
        private Integer maxPeople;

        private Integer budgetMin;

        private Integer budgetMax;

        private String expectedMateDesc;

        private String visibility;
    }

    @Data
    public static class SearchRequest {
        private String destination;
        private LocalDate startDate;
        private LocalDate endDate;
    }

    @Data
    public static class PostSummary {
        private Long id;
        private String destination;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer minPeople;
        private Integer maxPeople;
        private Integer budgetMin;
        private Integer budgetMax;
        private String status;
        private Long creatorId;
        private String creatorNickname;
        private String creatorAvatar;
        private Integer creatorReputationLevel;
        private String creatorTags; // 创建者的标签（逗号分隔）
        private Long relatedPlanId;
    }

    /** 结伴帖详情，含说明与可选关联小队 ID */
    @Data
    @EqualsAndHashCode(callSuper = false)
    public static class PostDetail extends PostSummary {
        private String expectedMateDesc;
        /** 该帖子下第一个小队 ID（若有），用于详情页展示成员 */
        private Long teamId;
    }

    @Data
    public static class TeamMemberItem {
        private Long userId;
        private String userName;
        private String avatar;
        private Integer reputationLevel;
        private String role;
        private String state;
    }

    @Data
    public static class TeamDetail {
        private Long id;
        private String name;
        private String status;
        private Long postId;
        private String destination;
        private LocalDate startDate;
        private LocalDate endDate;
        private Long relatedPlanId;
        /** 结伴帖最大人数，便于小队页展示 */
        private Integer maxPeople;
        private Integer budgetMin;
        private Integer budgetMax;
        private java.util.List<TeamMemberItem> members;
    }

    /** 结伴帖内置沟通单条消息（供前端展示） */
    @Data
    public static class PostChatMessageItem {
        private Long id;
        private Long userId;
        private String authorNickname;
        private String content;
        private String type;
        private String spotJson;
        private String routeJson;
        private String companionJson;
        private LocalDateTime createdAt;
    }

    /** 发送结伴帖内置聊天消息的请求体 */
    @Data
    public static class SendPostChatRequest {
        /** 文本消息时必填；图片时为 base64；景点/路线/结伴时为摘要文案 */
        private String content;
        /** 消息类型：text / spot / image / route / companion */
        private String type;
        private String spotJson;
        private String routeJson;
        private String companionJson;
    }

    /** 消息中心「小队消息」：当前用户所在小队及该帖最近一条聊天预览 */
    @Data
    public static class MyTeamMessageItem {
        private Long teamId;
        private Long postId;
        private String destination;
        private String lastMessagePreview;
        private LocalDateTime lastMessageTime;
        private Integer memberCount;
    }
}


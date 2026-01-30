package com.example.travel.social.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TravelNoteDtos {

    @Data
    public static class CreateRequest {
        @NotBlank
        private String title;

        @NotBlank
        private String content;

        private String coverImage;

        private Long relatedPlanId;

        private String destination;
    }

    /** 更新游记请求体，与创建字段一致 */
    @Data
    public static class UpdateRequest {
        @NotBlank
        private String title;

        @NotBlank
        private String content;

        private String coverImage;

        private Long relatedPlanId;

        private String destination;
    }

    @Data
    public static class Summary {
        private Long id;
        private String title;
        private String destination;
        private String coverImage;
        /** 作者用户 ID，用于跳转个人主页/私信 */
        private Long authorId;
        private String authorName;
        private LocalDateTime createdAt;
        /** 点赞数 */
        private Long likeCount;
        /** 评论数 */
        private Long commentCount;
    }

    @Data
    public static class Detail {
        private Long id;
        private String title;
        private String content;
        private String coverImage;
        private Long relatedPlanId;
        private String destination;
        /** 作者用户 ID，用于跳转个人主页/私信 */
        private Long authorId;
        private String authorName;
        private LocalDateTime createdAt;
    }

    /** 游记详情页「相关景点推荐」单项，由后端根据关联路线自动生成 */
    @Data
    public static class RelatedSpotItem {
        /** 前端列表 key，如 route-1-0 */
        private String id;
        /** 景点/活动名称 */
        private String name;
        /** 位置/距离描述 */
        private String location;
        /** 类型，如 景点、用餐、交通 */
        private String type;
        /** 时间范围，如 09:00–12:00 */
        private String timeRange;
        /** 封面图 URL，可为空由前端占位 */
        private String imageUrl;
        /** 来自关联路线时返回路线 ID，前端可跳转路线详情 */
        private Long routeId;
    }
}


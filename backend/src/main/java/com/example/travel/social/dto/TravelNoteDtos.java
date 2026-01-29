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
}


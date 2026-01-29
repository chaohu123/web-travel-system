package com.example.travel.social.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentDtos {

    @Data
    public static class CreateRequest {
        @NotBlank
        private String targetType;

        @NotNull
        private Long targetId;

        @NotBlank
        private String content;

        @Min(1)
        @Max(5)
        private Integer score;

        /** 可选评价标签，如 守时/好沟通/靠谱，前端可多选后传入 */
        private java.util.List<String> tags;
    }

    @Data
    public static class CommentItem {
        private Long id;
        private Long userId; // 评论用户ID，用于跳转个人主页
        private String userName;
        private String content;
        private Integer score;
        private LocalDateTime createdAt;
        private java.util.List<String> tags;
    }

    @Data
    public static class PagedResult {
        private java.util.List<CommentItem> list;
        private Long total;
        private Integer page;
        private Integer pageSize;
    }
}


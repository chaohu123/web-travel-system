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
    }

    @Data
    public static class CommentItem {
        private Long id;
        private String userName;
        private String content;
        private Integer score;
        private LocalDateTime createdAt;
    }
}


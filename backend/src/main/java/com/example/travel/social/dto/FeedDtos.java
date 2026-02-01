package com.example.travel.social.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeedDtos {

    @Data
    public static class CreateRequest {
        @NotBlank
        private String content;

        private String imageUrlsJson;
    }

    @Data
    public static class FeedItem {
        private Long id;
        private String content;
        private String imageUrlsJson;
        private Long authorId;
        private String authorName;
        private LocalDateTime createdAt;
    }
}


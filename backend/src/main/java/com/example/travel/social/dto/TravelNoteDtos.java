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

    @Data
    public static class Summary {
        private Long id;
        private String title;
        private String destination;
        private String coverImage;
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
        private String authorName;
        private LocalDateTime createdAt;
    }
}


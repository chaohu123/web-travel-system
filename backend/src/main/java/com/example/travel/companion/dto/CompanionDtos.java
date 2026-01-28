package com.example.travel.companion.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

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
        private String creatorNickname;
        private Long relatedPlanId;
    }

    /** 结伴帖详情，含说明与可选关联小队 ID */
    @Data
    public static class PostDetail extends PostSummary {
        private String expectedMateDesc;
        /** 该帖子下第一个小队 ID（若有），用于详情页展示成员 */
        private Long teamId;
    }

    @Data
    public static class TeamMemberItem {
        private Long userId;
        private String userName;
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
        private java.util.List<TeamMemberItem> members;
    }
}


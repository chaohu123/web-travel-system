package com.example.travel.route.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TripPlanDtos {

    @Data
    public static class CreateRequest {
        @NotBlank
        private String destination;

        @NotNull
        private LocalDate startDate;

        @NotNull
        private LocalDate endDate;

        @Min(0)
        private Integer budget;

        @Min(1)
        private Integer peopleCount;

        @NotBlank
        private String pace;

        // 简化：前端可传一个 JSON 字符串，如 {nature:0.8, culture:0.6...}
        private String preferenceWeightsJson;
    }

    @Data
    public static class Activity {
        private String type;
        private String name;
        private String location;
        private String startTime;
        private String endTime;
        private String transport;
        private Integer estimatedCost;
        /** 景点/活动经度 */
        private Double lng;
        /** 景点/活动纬度 */
        private Double lat;
    }

    @Data
    public static class Day {
        private Integer dayIndex;
        private LocalDate date;
        private List<Activity> activities;
    }

    @Data
    public static class PlanResponse {
        private Long id;
        private String title;
        private String destination;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer budget;
        private Integer peopleCount;
        private String pace;
        private List<Day> days;
    }
}


package com.example.travel.route.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class TripPlanDtos {

    /** AI 生成路线请求（不落库，仅返回多方案） */
    @Data
    public static class AiGenerateRequest {
        private String departureCity;
        @NotNull
        private List<String> destinations;
        @NotNull
        private LocalDate startDate;
        @NotNull
        private LocalDate endDate;
        @Min(0)
        private Integer totalBudget = 8000;
        @Min(1)
        private Integer peopleCount = 2;
        private String transport = "mixed";  // public / drive / mixed
        private String intensity = "moderate"; // relaxed / moderate / high
        /** 兴趣权重 JSON，如 {"nature":60,"culture":80,"food":70,"shopping":40,"relax":50} */
        private String interestWeightsJson;
    }

    /** AI 生成：单日内的一个 POI/活动项（与前端 POIItem 对齐），含经纬度供地图展示 */
    @Data
    public static class AiPoiItem {
        private String id;
        private String image;
        private String name;
        private Integer stayMinutes;
        private List<String> tags;
        private Double lng;
        private Double lat;
    }

    /** AI 生成：单日行程（与前端 DayPlan 对齐） */
    @Data
    public static class AiDayPlan {
        private Integer dayIndex;
        private LocalDate date;
        private Integer durationMinutes;
        private Integer distanceKm;
        private Integer commuteMinutes;
        private List<AiPoiItem> items;
    }

    /** AI 生成：一个方案（与前端 PlanVariant 对齐） */
    @Data
    public static class AiPlanVariant {
        private String id;
        private String name;
        private List<AiDayPlan> days;
    }

    /** AI 生成路线响应：多方案，供前端直接展示 */
    @Data
    public static class AiGenerateResponse {
        private List<AiPlanVariant> variants;
    }

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

        /** 可选：每日行程（含活动），保存后路线详情页可展示完整行程 */
        private List<Day> days;
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
        /** 该路线被游记/结伴引用次数，用于个人主页展示 */
        private Long usedCount;
    }
}


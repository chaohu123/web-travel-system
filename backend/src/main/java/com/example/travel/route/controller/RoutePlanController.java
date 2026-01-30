package com.example.travel.route.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.route.dto.TripPlanDtos;
import com.example.travel.route.entity.TripPlan;
import com.example.travel.route.service.RoutePlanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routes")
public class RoutePlanController {

    private final RoutePlanService routePlanService;

    public RoutePlanController(RoutePlanService routePlanService) {
        this.routePlanService = routePlanService;
    }

    @PostMapping
    public ApiResponse<Long> createPlan(@Valid @RequestBody TripPlanDtos.CreateRequest request) {
        TripPlan plan = routePlanService.createPlan(request);
        return ApiResponse.success(plan.getId());
    }

    @GetMapping("/my")
    public ApiResponse<List<TripPlanDtos.PlanResponse>> myPlans() {
        return ApiResponse.success(routePlanService.listMyPlans());
    }

    @GetMapping("/{id}")
    public ApiResponse<TripPlanDtos.PlanResponse> getOne(@PathVariable Long id) {
        return ApiResponse.success(routePlanService.getPlan(id));
    }

    /**
     * 热门路线（未登录可访问）
     * GET /api/routes/hot?limit=4
     */
    @GetMapping("/hot")
    public ApiResponse<List<TripPlanDtos.PlanResponse>> hot(@RequestParam(defaultValue = "4") int limit) {
        return ApiResponse.success(routePlanService.listHotPlans(limit));
    }
}


package com.example.travel.route.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.route.dto.TripPlanDtos;
import com.example.travel.route.entity.TripPlan;
import com.example.travel.route.repository.TripPlanRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoutePlanService {

    private final TripPlanRepository tripPlanRepository;
    private final UserRepository userRepository;

    public RoutePlanService(TripPlanRepository tripPlanRepository,
                            UserRepository userRepository) {
        this.tripPlanRepository = tripPlanRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return (username.contains("@")
                ? userRepository.findByEmail(username)
                : userRepository.findByPhone(username))
                .orElseThrow(() -> BusinessException.unauthorized("用户未登录"));
    }

    @Transactional
    public TripPlan createPlan(TripPlanDtos.CreateRequest req) {
        if (req.getEndDate().isBefore(req.getStartDate())) {
            throw BusinessException.badRequest("结束日期不能早于开始日期");
        }

        User current = getCurrentUser();

        TripPlan plan = new TripPlan();
        plan.setOwner(current);
        plan.setDestination(req.getDestination());
        plan.setStartDate(req.getStartDate());
        plan.setEndDate(req.getEndDate());
        plan.setBudget(req.getBudget());
        plan.setPeopleCount(req.getPeopleCount());
        plan.setPace(req.getPace());
        plan.setPreferenceWeightsJson(req.getPreferenceWeightsJson());
        plan.setTitle(current.getEmail() + "的" + req.getDestination() + "之旅");

        return tripPlanRepository.save(plan);
    }

    public List<TripPlanDtos.PlanResponse> listMyPlans() {
        User current = getCurrentUser();
        List<TripPlan> plans = tripPlanRepository.findByOwnerOrderByCreatedAtDesc(current);
        return plans.stream().map(this::toResponseWithoutActivities).collect(Collectors.toList());
    }

    public TripPlanDtos.PlanResponse getPlan(Long id) {
        TripPlan plan = tripPlanRepository.findById(id)
                .orElseThrow(() -> BusinessException.badRequest("行程不存在"));

        return toResponseWithSimpleActivities(plan);
    }

    private TripPlanDtos.PlanResponse toResponseWithoutActivities(TripPlan plan) {
        TripPlanDtos.PlanResponse resp = new TripPlanDtos.PlanResponse();
        resp.setId(plan.getId());
        resp.setTitle(plan.getTitle());
        resp.setDestination(plan.getDestination());
        resp.setStartDate(plan.getStartDate());
        resp.setEndDate(plan.getEndDate());
        resp.setBudget(plan.getBudget());
        resp.setPeopleCount(plan.getPeopleCount());
        resp.setPace(plan.getPace());
        resp.setDays(List.of());
        return resp;
    }

    /**
     * 简化版：按照日期区间自动生成每天 3 个占位活动，后续可换为 AI/规则引擎。
     */
    private TripPlanDtos.PlanResponse toResponseWithSimpleActivities(TripPlan plan) {
        TripPlanDtos.PlanResponse resp = toResponseWithoutActivities(plan);
        List<TripPlanDtos.Day> days = new ArrayList<>();
        long daysCount = ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate()) + 1;
        for (int i = 0; i < daysCount; i++) {
            LocalDate date = plan.getStartDate().plusDays(i);
            TripPlanDtos.Day day = new TripPlanDtos.Day();
            day.setDayIndex(i + 1);
            day.setDate(date);

            List<TripPlanDtos.Activity> activities = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                TripPlanDtos.Activity a = new TripPlanDtos.Activity();
                a.setType("sight");
                a.setName("待定景点 " + (j + 1));
                a.setLocation(plan.getDestination());
                a.setStartTime((9 + j * 3) + ":00");
                a.setEndTime((11 + j * 3) + ":00");
                a.setTransport("待定");
                a.setEstimatedCost(0);
                activities.add(a);
            }
            day.setActivities(activities);
            days.add(day);
        }
        resp.setDays(days);
        return resp;
    }
}


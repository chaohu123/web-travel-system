package com.example.travel.route.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.route.dto.TripPlanDtos;
import com.example.travel.route.entity.TripPlan;
import com.example.travel.route.repository.TripActivityRepository;
import com.example.travel.route.repository.TripDayRepository;
import com.example.travel.route.repository.TripPlanRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(RoutePlanService.class);

    private final TripPlanRepository tripPlanRepository;
    private final TripDayRepository tripDayRepository;
    private final TripActivityRepository tripActivityRepository;
    private final UserRepository userRepository;

    public RoutePlanService(TripPlanRepository tripPlanRepository,
                            TripDayRepository tripDayRepository,
                            TripActivityRepository tripActivityRepository,
                            UserRepository userRepository) {
        this.tripPlanRepository = tripPlanRepository;
        this.tripDayRepository = tripDayRepository;
        this.tripActivityRepository = tripActivityRepository;
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

        TripPlanDtos.PlanResponse resp = toResponseWithDbActivities(plan);
        if (log.isDebugEnabled()) {
            int dayCount = resp.getDays() != null ? resp.getDays().size() : 0;
            int actCount = resp.getDays() == null ? 0 : resp.getDays().stream()
                    .mapToInt(d -> d.getActivities() == null ? 0 : d.getActivities().size())
                    .sum();
            log.debug("[RoutePlan] Loaded plan {} with {} days, {} activities", id, dayCount, actCount);
        }
        return resp;
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
     * 使用数据库中实际的 TripDay / TripActivity 构建返回结果。
     * 若某些活动尚未配置经纬度，lng/lat 为 null，由前端自行地理编码兜底。
     */
    private TripPlanDtos.PlanResponse toResponseWithDbActivities(TripPlan plan) {
        TripPlanDtos.PlanResponse resp = toResponseWithoutActivities(plan);
        List<TripPlanDtos.Day> days = new ArrayList<>();

        // 读取该行程下所有天
        var tripDays = tripDayRepository.findByPlanOrderByDayIndexAsc(plan);
        for (var tripDay : tripDays) {
            TripPlanDtos.Day day = new TripPlanDtos.Day();
            day.setDayIndex(tripDay.getDayIndex());
            day.setDate(tripDay.getDate());

            // 读取当天的所有活动
            var activities = new ArrayList<TripPlanDtos.Activity>();
            tripActivityRepository.findByTripDayOrderByStartTimeAscIdAsc(tripDay)
                    .forEach(act -> {
                        TripPlanDtos.Activity a = new TripPlanDtos.Activity();
                        a.setType(act.getType());
                        a.setName(act.getName());
                        a.setLocation(act.getLocation());
                        a.setStartTime(act.getStartTime());
                        a.setEndTime(act.getEndTime());
                        a.setTransport(act.getTransport());
                        a.setEstimatedCost(act.getEstimatedCost());
                        a.setLng(act.getLng());
                        a.setLat(act.getLat());
                        activities.add(a);
                    });

            day.setActivities(activities);
            days.add(day);
        }

        resp.setDays(days);
        return resp;
    }
}


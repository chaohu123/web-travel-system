package com.example.travel.route.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.route.dto.TripPlanDtos;
import com.example.travel.route.entity.TripPlan;
import com.example.travel.companion.repository.CompanionPostRepository;
import com.example.travel.route.repository.TripActivityRepository;
import com.example.travel.route.repository.TripDayRepository;
import com.example.travel.route.repository.TripPlanRepository;
import com.example.travel.social.repository.ContentFavoriteRepository;
import com.example.travel.social.repository.ContentLikeRepository;
import com.example.travel.social.repository.TravelNoteRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoutePlanService {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanService.class);

    private final TripPlanRepository tripPlanRepository;
    private final TripDayRepository tripDayRepository;
    private final TripActivityRepository tripActivityRepository;
    private final ContentLikeRepository contentLikeRepository;
    private final ContentFavoriteRepository contentFavoriteRepository;
    private final TravelNoteRepository travelNoteRepository;
    private final CompanionPostRepository companionPostRepository;
    private final UserRepository userRepository;

    public RoutePlanService(TripPlanRepository tripPlanRepository,
                            TripDayRepository tripDayRepository,
                            TripActivityRepository tripActivityRepository,
                            ContentLikeRepository contentLikeRepository,
                            ContentFavoriteRepository contentFavoriteRepository,
                            TravelNoteRepository travelNoteRepository,
                            CompanionPostRepository companionPostRepository,
                            UserRepository userRepository) {
        this.tripPlanRepository = tripPlanRepository;
        this.tripDayRepository = tripDayRepository;
        this.tripActivityRepository = tripActivityRepository;
        this.contentLikeRepository = contentLikeRepository;
        this.contentFavoriteRepository = contentFavoriteRepository;
        this.travelNoteRepository = travelNoteRepository;
        this.companionPostRepository = companionPostRepository;
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
            if (resp.getDays() != null) {
                for (TripPlanDtos.Day d : resp.getDays()) {
                    int n = d.getActivities() != null ? d.getActivities().size() : 0;
                    String names = d.getActivities() == null ? "" : d.getActivities().stream()
                            .map(a -> a.getName() != null ? a.getName() : (a.getLocation() != null ? a.getLocation() : "?"))
                            .limit(3)
                            .reduce((a, b) -> a + "," + b)
                            .orElse("");
                    log.debug("[RoutePlan]   dayIndex={} (type={}) activities={} names=[{}]",
                            d.getDayIndex(), d.getDayIndex() != null ? d.getDayIndex().getClass().getSimpleName() : "null", n, names);
                }
            }
        }
        return resp;
    }

    /**
     * 热门路线：按热度（点赞+收藏）倒序返回前 N 条。
     * 为避免复杂 SQL，本版本从最新 50 条路线中计算热度（数据量小的场景足够）。
     */
    public List<TripPlanDtos.PlanResponse> listHotPlans(int limit) {
        int n = Math.max(1, Math.min(limit <= 0 ? 4 : limit, 50));
        List<TripPlan> candidates = tripPlanRepository.findTop50ByOrderByCreatedAtDesc();
        if (candidates.isEmpty()) return List.of();

        // 计算热度：likeCount + favoriteCount
        List<ScoredPlan> scored = candidates.stream().map(p -> {
            long likes = contentLikeRepository.countByTargetTypeAndTargetId("route", p.getId());
            long favs = contentFavoriteRepository.countByTargetTypeAndTargetId("route", p.getId());
            return new ScoredPlan(p, likes + favs);
        }).collect(Collectors.toList());

        return scored.stream()
                .sorted(Comparator
                        .comparingLong(ScoredPlan::score).reversed()
                        .thenComparing(sp -> sp.plan().getCreatedAt(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(n)
                .map(sp -> toResponseWithoutActivities(sp.plan()))
                .collect(Collectors.toList());
    }

    private record ScoredPlan(TripPlan plan, long score) {}

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
        long usedByNotes = travelNoteRepository.countByRelatedPlanId(plan.getId());
        long usedByCompanion = companionPostRepository.countByRelatedPlanId(plan.getId());
        resp.setUsedCount(usedByNotes + usedByCompanion);
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


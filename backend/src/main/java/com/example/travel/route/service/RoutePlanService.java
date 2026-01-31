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
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.repository.UserProfileRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Map.entry;

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
    private final UserProfileRepository userProfileRepository;
    private final AiRouteClient aiRouteClient;

    public RoutePlanService(TripPlanRepository tripPlanRepository,
                            TripDayRepository tripDayRepository,
                            TripActivityRepository tripActivityRepository,
                            ContentLikeRepository contentLikeRepository,
                            ContentFavoriteRepository contentFavoriteRepository,
                            TravelNoteRepository travelNoteRepository,
                            CompanionPostRepository companionPostRepository,
                            UserRepository userRepository,
                            UserProfileRepository userProfileRepository,
                            AiRouteClient aiRouteClient) {
        this.tripPlanRepository = tripPlanRepository;
        this.tripDayRepository = tripDayRepository;
        this.tripActivityRepository = tripActivityRepository;
        this.contentLikeRepository = contentLikeRepository;
        this.contentFavoriteRepository = contentFavoriteRepository;
        this.travelNoteRepository = travelNoteRepository;
        this.companionPostRepository = companionPostRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.aiRouteClient = aiRouteClient;
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
        log.info("[RoutePlan] createPlan: destination={}, start={}, end={}, days={}",
                req.getDestination(), req.getStartDate(), req.getEndDate(),
                req.getDays() != null ? req.getDays().size() : 0);
        if (req.getEndDate().isBefore(req.getStartDate())) {
            log.warn("[RoutePlan] createPlan failed: 结束日期不能早于开始日期");
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
        String displayName = "用户";
        UserProfile profile = userProfileRepository.findById(current.getId()).orElse(null);
        if (profile != null && profile.getNickname() != null && !profile.getNickname().isBlank()) {
            displayName = profile.getNickname();
        } else if (current.getEmail() != null && !current.getEmail().isEmpty()) {
            displayName = current.getEmail();
        } else if (current.getPhone() != null && !current.getPhone().isEmpty()) {
            displayName = current.getPhone();
        }
        plan.setTitle(displayName + "的" + req.getDestination() + "之旅");

        plan = tripPlanRepository.save(plan);

        if (req.getDays() != null && !req.getDays().isEmpty()) {
            for (TripPlanDtos.Day dto : req.getDays()) {
                var tripDay = new com.example.travel.route.entity.TripDay();
                tripDay.setPlan(plan);
                tripDay.setDayIndex(dto.getDayIndex());
                tripDay.setDate(dto.getDate());
                tripDay = tripDayRepository.save(tripDay);
                if (dto.getActivities() != null) {
                    for (TripPlanDtos.Activity a : dto.getActivities()) {
                        var act = new com.example.travel.route.entity.TripActivity();
                        act.setTripDay(tripDay);
                        act.setType(a.getType() != null ? a.getType() : "sight");
                        act.setName(a.getName());
                        act.setLocation(a.getLocation());
                        act.setStartTime(a.getStartTime());
                        act.setEndTime(a.getEndTime());
                        act.setTransport(a.getTransport());
                        act.setEstimatedCost(a.getEstimatedCost());
                        act.setLng(a.getLng());
                        act.setLat(a.getLat());
                        tripActivityRepository.save(act);
                    }
                }
            }
        }

        log.info("[RoutePlan] createPlan success: planId={}", plan.getId());
        return plan;
    }

    /**
     * 删除路线（仅创建者可删）
     */
    @Transactional
    public void deletePlan(Long id) {
        TripPlan plan = tripPlanRepository.findById(id)
                .orElseThrow(() -> BusinessException.badRequest("行程不存在"));
        User current = getCurrentUser();
        if (!current.getId().equals(plan.getOwner().getId())) {
            throw BusinessException.forbidden("只能删除自己创建的路线");
        }
        var days = tripDayRepository.findByPlanOrderByDayIndexAsc(plan);
        for (var day : days) {
            tripActivityRepository.findByTripDayOrderByStartTimeAscIdAsc(day)
                    .forEach(tripActivityRepository::delete);
            tripDayRepository.delete(day);
        }
        tripPlanRepository.delete(plan);
        log.info("[RoutePlan] deletePlan: planId={}", id);
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

    /** 将分钟数转为 "HH:mm"；用于无时间段时的默认展示 */
    private static String formatMinutesToTime(int totalMinutes) {
        int h = (totalMinutes / 60) % 24;
        int m = totalMinutes % 60;
        return String.format("%02d:%02d", h, m);
    }

    /** 根据用户资料解析展示名：昵称优先，其次邮箱、手机号，最后「用户」。用于列表/详情展示，避免旧数据标题仍为邮箱。 */
    private String getOwnerDisplayName(User user) {
        if (user == null) return "用户";
        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        if (profile != null && profile.getNickname() != null && !profile.getNickname().isBlank()) {
            return profile.getNickname();
        }
        if (user.getEmail() != null && !user.getEmail().isEmpty()) return user.getEmail();
        if (user.getPhone() != null && !user.getPhone().isEmpty()) return user.getPhone();
        return "用户";
    }

    private TripPlanDtos.PlanResponse toResponseWithoutActivities(TripPlan plan) {
        TripPlanDtos.PlanResponse resp = new TripPlanDtos.PlanResponse();
        resp.setId(plan.getId());
        String displayTitle = getOwnerDisplayName(plan.getOwner()) + "的" + (plan.getDestination() != null ? plan.getDestination() : "") + "之旅";
        resp.setTitle(displayTitle);
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

            // 读取当天的所有活动；若无时间段则按顺序分配默认 09:00 起、每项约 1h+间隔 30min
            var rawActivities = tripActivityRepository.findByTripDayOrderByStartTimeAscIdAsc(tripDay);
            var activities = new ArrayList<TripPlanDtos.Activity>();
            int cursorMinutes = 9 * 60;
            for (var act : rawActivities) {
                TripPlanDtos.Activity a = new TripPlanDtos.Activity();
                a.setType(act.getType());
                a.setName(act.getName());
                a.setLocation(act.getLocation());
                String startTime = act.getStartTime();
                String endTime = act.getEndTime();
                if (startTime == null || startTime.isBlank() || endTime == null || endTime.isBlank()) {
                    a.setStartTime(formatMinutesToTime(cursorMinutes));
                    a.setEndTime(formatMinutesToTime(cursorMinutes + 60));
                    cursorMinutes += 60 + 30;
                } else {
                    a.setStartTime(startTime);
                    a.setEndTime(endTime);
                }
                a.setTransport(act.getTransport());
                a.setEstimatedCost(act.getEstimatedCost());
                a.setLng(act.getLng());
                a.setLat(act.getLat());
                activities.add(a);
            }

            day.setActivities(activities);
            days.add(day);
        }

        resp.setDays(days);
        return resp;
    }

    /**
     * AI 生成路线方案（不落库）：根据前端表单参数调用 AI API 生成多套方案；
     * 若未配置 API Key 或调用失败，则回退为 mock 数据。
     */
    public TripPlanDtos.AiGenerateResponse generateAiPlan(TripPlanDtos.AiGenerateRequest req) {
        if (req.getDestinations() == null || req.getDestinations().isEmpty()) {
            throw BusinessException.badRequest("请至少添加一个目的地");
        }
        LocalDate start = req.getStartDate();
        LocalDate end = req.getEndDate();
        if (end.isBefore(start)) {
            throw BusinessException.badRequest("结束日期不能早于出发日期");
        }
        int dayCount = (int) ChronoUnit.DAYS.between(start, end) + 1;
        dayCount = Math.max(1, Math.min(dayCount, 14));

        TripPlanDtos.AiGenerateResponse resp = null;
        if (!aiRouteClient.isAvailable()) {
            log.info("[AI路线] 未配置或未启用 AI，使用 mock 数据。若要调用 DeepSeek/OpenAI：请在 IDEA 运行配置中添加环境变量 OPENAI_API_KEY=你的key，或配置 app.ai.api-key");
        }
        if (aiRouteClient.isAvailable()) {
            log.info("[AI路线] 使用 AI 生成，请求: 出发地={}, 目的地={}, 日期={} ~ {}, 预算={}, 交通={}, 节奏={}",
                    req.getDepartureCity(), req.getDestinations(), req.getStartDate(), req.getEndDate(),
                    req.getTotalBudget(), req.getTransport(), req.getIntensity());
            resp = aiRouteClient.generate(req);
            if (resp != null && resp.getVariants() != null && !resp.getVariants().isEmpty()) {
                fillPoiCoordsForResponse(resp);
                log.info("[AI路线] AI 生成成功，返回方案数: {}, 完整数据见 DEBUG 日志", resp.getVariants().size());
                log.debug("[AI路线] 返回给前端的完整数据: {}", resp);
                return resp;
            }
        }
        log.info("[AI路线] 未配置或调用失败，使用 mock 数据");
        String destination = req.getDestinations().isEmpty() ? "" : req.getDestinations().get(0);
        List<TripPlanDtos.AiPlanVariant> variants = new ArrayList<>();
        variants.add(buildMockVariant("a", "方案 A（文化优先）", "culture", start, dayCount, destination));
        variants.add(buildMockVariant("b", "方案 B（自然优先）", "nature", start, dayCount, destination));
        variants.add(buildMockVariant("c", "方案 C（轻松休闲）", "relax", start, dayCount, destination));
        resp = new TripPlanDtos.AiGenerateResponse();
        resp.setVariants(variants);
        log.debug("[AI路线] mock 返回给前端的完整数据: {}", resp);
        return resp;
    }

    /** 为 AI 返回的 POI 按名称匹配填充经纬度，供前端地图展示 */
    private void fillPoiCoordsForResponse(TripPlanDtos.AiGenerateResponse resp) {
        if (resp.getVariants() == null) return;
        for (TripPlanDtos.AiPlanVariant v : resp.getVariants()) {
            if (v.getDays() == null) continue;
            for (TripPlanDtos.AiDayPlan d : v.getDays()) {
                if (d.getItems() == null) continue;
                for (TripPlanDtos.AiPoiItem item : d.getItems()) {
                    if (item.getLng() != null && item.getLat() != null) continue;
                    double[] coords = getPoiCoords("", item.getName());
                    if (coords != null) {
                        item.setLng(coords[0]);
                        item.setLat(coords[1]);
                    }
                }
            }
        }
    }

    private TripPlanDtos.AiPlanVariant buildMockVariant(String id, String name, String type,
                                                        LocalDate startDate, int dayCount, String destination) {
        List<TripPlanDtos.AiDayPlan> days = new ArrayList<>();
        String city = destination == null ? "" : destination.trim();

        // 苏州：文化 / 自然 / 休闲 三套
        List<String[]> suzhouCulture = List.of(
                new String[]{"拙政园", "文化,园林", "150"},
                new String[]{"苏州博物馆", "文化,历史", "120"},
                new String[]{"狮子林", "文化,园林", "90"},
                new String[]{"虎丘", "文化,自然", "120"},
                new String[]{"寒山寺", "文化,宗教", "90"}
        );
        List<String[]> suzhouNature = List.of(
                new String[]{"金鸡湖", "自然,休闲", "120"},
                new String[]{"阳澄湖", "自然,美食", "150"},
                new String[]{"太湖湿地", "自然,生态", "180"},
                new String[]{"平江路", "自然,文化", "90"},
                new String[]{"同里古镇", "自然,古镇", "150"}
        );
        List<String[]> suzhouRelax = List.of(
                new String[]{"平江路漫步", "休闲,文化", "90"},
                new String[]{"山塘街", "休闲,美食", "120"},
                new String[]{"观前街", "休闲,购物", "90"},
                new String[]{"苏州评弹", "休闲,文化", "60"},
                new String[]{"苏帮菜馆", "美食,文化", "90"}
        );

        // 上海：文化 / 自然 / 休闲
        List<String[]> shanghaiCulture = List.of(
                new String[]{"豫园", "文化,园林", "120"},
                new String[]{"上海博物馆", "文化,历史", "150"},
                new String[]{"中共一大会址", "文化,历史", "90"},
                new String[]{"田子坊", "文化,创意", "90"},
                new String[]{"新天地", "文化,休闲", "120"}
        );
        List<String[]> shanghaiNature = List.of(
                new String[]{"外滩", "自然,景观", "120"},
                new String[]{"世纪公园", "自然,休闲", "150"},
                new String[]{"朱家角古镇", "自然,古镇", "180"},
                new String[]{"滨江森林公园", "自然,生态", "120"},
                new String[]{"东方明珠", "自然,地标", "90"}
        );
        List<String[]> shanghaiRelax = List.of(
                new String[]{"南京路步行街", "休闲,购物", "120"},
                new String[]{"田子坊", "休闲,美食", "90"},
                new String[]{"新天地", "休闲,文化", "90"},
                new String[]{"外滩夜景", "休闲,景观", "60"},
                new String[]{"城隍庙小吃", "美食,文化", "90"}
        );

        // 北京 / 杭州 / 其他：沿用原 mock
        List<String[]> beijingCulture = List.of(
                new String[]{"故宫博物院", "文化,历史", "180"},
                new String[]{"国家博物馆", "文化,历史", "120"},
                new String[]{"南锣鼓巷", "文化,美食", "90"},
                new String[]{"颐和园", "文化,自然", "150"},
                new String[]{"雍和宫", "文化,宗教", "90"}
        );
        List<String[]> hangzhouNature = List.of(
                new String[]{"西湖", "自然,休闲", "120"},
                new String[]{"灵隐寺", "自然,文化", "90"},
                new String[]{"西溪湿地", "自然,生态", "180"},
                new String[]{"九溪烟树", "自然,徒步", "90"},
                new String[]{"龙井村", "自然,美食", "120"}
        );
        List<String[]> genericRelax = List.of(
                new String[]{"古镇漫步", "休闲,文化", "120"},
                new String[]{"温泉酒店", "休闲,放松", "180"},
                new String[]{"咖啡馆", "休闲,美食", "60"},
                new String[]{"夜市", "美食,购物", "90"},
                new String[]{"海边栈道", "休闲,自然", "90"}
        );

        List<String[]> culturePool;
        List<String[]> naturePool;
        List<String[]> relaxPool;
        if (city.contains("苏州")) {
            culturePool = suzhouCulture;
            naturePool = suzhouNature;
            relaxPool = suzhouRelax;
        } else if (city.contains("上海")) {
            culturePool = shanghaiCulture;
            naturePool = shanghaiNature;
            relaxPool = shanghaiRelax;
        } else if (city.contains("北京")) {
            culturePool = beijingCulture;
            naturePool = List.of(
                    new String[]{"颐和园", "自然,文化", "150"},
                    new String[]{"北海公园", "自然,休闲", "120"},
                    new String[]{"香山", "自然,徒步", "180"},
                    new String[]{"奥森公园", "自然,生态", "120"},
                    new String[]{"什刹海", "自然,文化", "90"}
            );
            relaxPool = genericRelax;
        } else if (city.contains("杭州")) {
            culturePool = List.of(
                    new String[]{"灵隐寺", "文化,宗教", "90"},
                    new String[]{"宋城", "文化,演艺", "180"},
                    new String[]{"河坊街", "文化,美食", "90"},
                    new String[]{"中国美院", "文化,艺术", "120"},
                    new String[]{"六和塔", "文化,历史", "60"}
            );
            naturePool = hangzhouNature;
            relaxPool = genericRelax;
        } else {
            culturePool = beijingCulture;
            naturePool = hangzhouNature;
            relaxPool = genericRelax;
        }

        List<String[]> pool = "culture".equals(type) ? culturePool : "nature".equals(type) ? naturePool : relaxPool;

        for (int i = 0; i < dayCount; i++) {
            LocalDate d = startDate.plusDays(i);
            int n = 2 + (i % 2);
            List<TripPlanDtos.AiPoiItem> items = new ArrayList<>();
            int totalStay = 0;
            for (int j = 0; j < n; j++) {
                String[] row = pool.get((i * 2 + j) % pool.size());
                String poiName = row[0];
                TripPlanDtos.AiPoiItem item = new TripPlanDtos.AiPoiItem();
                item.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 9));
                item.setImage("https://picsum.photos/seed/poi" + (i * 10 + j) + "/320/180");
                item.setName(poiName);
                int stay = Integer.parseInt(row[2]);
                item.setStayMinutes(stay);
                totalStay += stay;
                item.setTags(List.of(row[1].split(",")));
                double[] coords = getPoiCoords(city, poiName);
                if (coords != null) {
                    item.setLng(coords[0]);
                    item.setLat(coords[1]);
                }
                items.add(item);
            }
            TripPlanDtos.AiDayPlan day = new TripPlanDtos.AiDayPlan();
            day.setDayIndex(i + 1);
            day.setDate(d);
            day.setDurationMinutes(totalStay + 30 * Math.max(0, items.size() - 1));
            day.setDistanceKm(12 + i * 8);
            day.setCommuteMinutes(20 + i * 10);
            day.setItems(items);
            days.add(day);
        }

        TripPlanDtos.AiPlanVariant variant = new TripPlanDtos.AiPlanVariant();
        variant.setId(id);
        variant.setName(name);
        variant.setDays(days);
        return variant;
    }

    /** 常用 POI 经纬度（高德坐标系），供地图展示 */
    private static final Map<String, double[]> POI_COORDS = Map.ofEntries(
            // 北京
            entry("故宫博物院", new double[]{116.397, 39.916}),
            entry("国家博物馆", new double[]{116.398, 39.904}),
            entry("南锣鼓巷", new double[]{116.404, 39.934}),
            entry("颐和园", new double[]{116.271, 39.999}),
            entry("雍和宫", new double[]{116.417, 39.949}),
            entry("北海公园", new double[]{116.383, 39.924}),
            entry("香山", new double[]{116.193, 39.998}),
            entry("奥森公园", new double[]{116.391, 40.016}),
            entry("什刹海", new double[]{116.382, 39.938}),
            entry("古镇漫步", new double[]{116.397, 39.916}),
            entry("温泉酒店", new double[]{116.397, 39.916}),
            entry("咖啡馆", new double[]{116.397, 39.916}),
            entry("夜市", new double[]{116.397, 39.916}),
            entry("海边栈道", new double[]{116.397, 39.916}),
            // 苏州
            entry("拙政园", new double[]{120.624, 31.323}),
            entry("苏州博物馆", new double[]{120.629, 31.321}),
            entry("狮子林", new double[]{120.631, 31.322}),
            entry("虎丘", new double[]{120.573, 31.302}),
            entry("寒山寺", new double[]{120.557, 31.311}),
            entry("金鸡湖", new double[]{120.681, 31.316}),
            entry("阳澄湖", new double[]{120.823, 31.421}),
            entry("太湖湿地", new double[]{120.412, 31.228}),
            entry("平江路", new double[]{120.636, 31.319}),
            entry("同里古镇", new double[]{120.716, 31.161}),
            entry("平江路漫步", new double[]{120.636, 31.319}),
            entry("山塘街", new double[]{120.601, 31.318}),
            entry("观前街", new double[]{120.629, 31.315}),
            entry("苏州评弹", new double[]{120.629, 31.315}),
            entry("苏帮菜馆", new double[]{120.629, 31.315}),
            // 上海
            entry("豫园", new double[]{121.491, 31.228}),
            entry("上海博物馆", new double[]{121.473, 31.230}),
            entry("中共一大会址", new double[]{121.473, 31.220}),
            entry("田子坊", new double[]{121.464, 31.214}),
            entry("新天地", new double[]{121.474, 31.216}),
            entry("外滩", new double[]{121.490, 31.239}),
            entry("世纪公园", new double[]{121.551, 31.228}),
            entry("朱家角古镇", new double[]{121.050, 31.108}),
            entry("滨江森林公园", new double[]{121.558, 31.382}),
            entry("东方明珠", new double[]{121.499, 31.239}),
            entry("南京路步行街", new double[]{121.478, 31.238}),
            entry("外滩夜景", new double[]{121.490, 31.239}),
            entry("城隍庙小吃", new double[]{121.491, 31.227}),
            // 杭州
            entry("西湖", new double[]{120.155, 30.274}),
            entry("雷峰塔", new double[]{120.149, 30.231}),
            entry("灵隐寺", new double[]{120.096, 30.241}),
            entry("中国茶叶博物馆", new double[]{120.130, 30.257}),
            entry("宋城", new double[]{120.111, 30.206}),
            entry("河坊街", new double[]{120.164, 30.242}),
            entry("西溪湿地", new double[]{120.053, 30.270}),
            entry("龙井村", new double[]{120.109, 30.228}),
            entry("九溪烟树", new double[]{120.123, 30.218}),
            entry("六和塔", new double[]{120.131, 30.197}),
            entry("中国美院", new double[]{120.154, 30.259}),
            entry("断桥残雪", new double[]{120.147, 30.263}),
            entry("苏堤", new double[]{120.142, 30.252}),
            entry("白堤", new double[]{120.148, 30.265}),
            entry("钱塘江", new double[]{120.210, 30.208}),
            entry("千岛湖", new double[]{119.019, 29.605})
    );

    private double[] getPoiCoords(String city, String poiName) {
        if (poiName == null || poiName.isEmpty()) return null;
        return POI_COORDS.get(poiName);
    }
}


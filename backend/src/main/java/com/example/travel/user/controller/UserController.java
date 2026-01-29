package com.example.travel.user.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.user.dto.UserDtos;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.entity.UserReputation;
import com.example.travel.user.entity.UserPreference;
import com.example.travel.user.repository.UserProfileRepository;
import com.example.travel.user.repository.UserRepository;
import com.example.travel.user.repository.UserReputationRepository;
import com.example.travel.user.repository.UserPreferenceRepository;
import com.example.travel.user.repository.UserFollowRepository;
import com.example.travel.route.repository.TripPlanRepository;
import com.example.travel.social.repository.TravelNoteRepository;
import com.example.travel.companion.repository.CompanionPostRepository;
import com.example.travel.companion.entity.CompanionPost;
import com.example.travel.route.entity.TripPlan;
import com.example.travel.social.entity.TravelNote;
import com.example.travel.user.dto.UserDtos;
import com.example.travel.social.dto.TravelNoteDtos;
import com.example.travel.route.dto.TripPlanDtos;
import com.example.travel.companion.dto.CompanionDtos;
import com.example.travel.social.dto.CommentDtos;
import com.example.travel.social.repository.CommentRepository;
import com.example.travel.social.repository.ContentLikeRepository;
import com.example.travel.social.repository.ContentFavoriteRepository;
import com.example.travel.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserReputationRepository userReputationRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserFollowRepository userFollowRepository;
    private final TripPlanRepository tripPlanRepository;
    private final TravelNoteRepository travelNoteRepository;
    private final CompanionPostRepository companionPostRepository;
    private final CommentRepository commentRepository;
    private final ContentLikeRepository contentLikeRepository;
    private final ContentFavoriteRepository contentFavoriteRepository;

    public UserController(UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          UserReputationRepository userReputationRepository,
                          UserPreferenceRepository userPreferenceRepository,
                          UserFollowRepository userFollowRepository,
                          TripPlanRepository tripPlanRepository,
                          TravelNoteRepository travelNoteRepository,
                          CompanionPostRepository companionPostRepository,
                          CommentRepository commentRepository,
                          ContentLikeRepository contentLikeRepository,
                          ContentFavoriteRepository contentFavoriteRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userReputationRepository = userReputationRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.userFollowRepository = userFollowRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.travelNoteRepository = travelNoteRepository;
        this.companionPostRepository = companionPostRepository;
        this.commentRepository = commentRepository;
        this.contentLikeRepository = contentLikeRepository;
        this.contentFavoriteRepository = contentFavoriteRepository;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = username.contains("@")
                ? userRepository.findByEmail(username).orElse(null)
                : userRepository.findByPhone(username).orElse(null);

        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        Map<String, Object> data = Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "phone", user.getPhone()
        );
        return ApiResponse.success(data);
    }

    @GetMapping("/me/detail")
    public ApiResponse<UserDtos.MeDetail> meDetail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = username.contains("@")
                ? userRepository.findByEmail(username).orElse(null)
                : userRepository.findByPhone(username).orElse(null);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        UserReputation reputation = userReputationRepository.findById(user.getId()).orElse(null);

        UserDtos.MeDetail dto = new UserDtos.MeDetail();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        if (profile != null) {
            dto.setNickname(profile.getNickname());
            dto.setAvatar(profile.getAvatar());
            dto.setCity(profile.getCity());
            dto.setGender(profile.getGender());
            dto.setAge(profile.getAge());
            dto.setIntro(profile.getIntro());
            dto.setSlogan(profile.getSlogan());
        }
        if (reputation != null) {
            dto.setReputationScore(reputation.getScore());
            dto.setReputationLevel(reputation.getLevel());
        }

        return ApiResponse.success(dto);
    }

    @PutMapping("/me/profile")
    public ApiResponse<UserDtos.MeDetail> updateProfile(@Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = username.contains("@")
                ? userRepository.findByEmail(username).orElse(null)
                : userRepository.findByPhone(username).orElse(null);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(new UserProfile());
        if (profile.getUser() == null) {
            profile.setUser(user);
            profile.setId(user.getId());
        }
        if (request.getNickname() != null) profile.setNickname(request.getNickname());
        if (request.getAvatar() != null) profile.setAvatar(request.getAvatar());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getAge() != null) profile.setAge(request.getAge());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getIntro() != null) profile.setIntro(request.getIntro());
        if (request.getSlogan() != null) profile.setSlogan(request.getSlogan());
        userProfileRepository.save(profile);
        return meDetail();
    }

    // ---------- 公开个人主页相关接口 ----------

    @GetMapping("/{userId}/homepage")
    public ApiResponse<UserDtos.UserPublicProfile> getUserHomepage(@PathVariable Long userId) {
        User target = userRepository.findById(userId).orElseThrow(() -> BusinessException.badRequest("用户不存在"));

        UserProfile profile = userProfileRepository.findById(userId).orElse(null);
        UserReputation reputation = userReputationRepository.findById(userId).orElse(null);
        UserPreference preference = userPreferenceRepository.findById(userId).orElse(null);

        UserDtos.UserPublicProfile dto = new UserDtos.UserPublicProfile();
        dto.setId(target.getId());
        if (profile != null) {
            dto.setNickname(profile.getNickname());
            dto.setAvatar(profile.getAvatar());
            dto.setCity(profile.getCity());
            dto.setGender(profile.getGender());
            dto.setAge(profile.getAge());
            dto.setIntro(profile.getIntro());
            dto.setSlogan(profile.getSlogan());
            // 这里暂未单独建封面字段，如有需要可扩展 t_user_profile
            dto.setCoverImage(null);
        }
        // 昵称兜底：如果档案中没有设置昵称，则回退到邮箱或手机号，避免前端显示“旅友”
        if (dto.getNickname() == null || dto.getNickname().isBlank()) {
            String name = target.getEmail() != null ? target.getEmail() : target.getPhone();
            dto.setNickname(name != null ? name : "旅友");
        }
        if (reputation != null) {
            dto.setReputationScore(reputation.getScore());
            dto.setReputationLevel(reputation.getLevel());
        }

        // 关注/粉丝统计
        long followers = userFollowRepository.countByFollowee(target);
        long following = userFollowRepository.countByFollower(target);
        dto.setFollowersCount(followers);
        dto.setFollowingCount(following);

        // 当前登录用户是否已关注
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            User current = username.contains("@")
                    ? userRepository.findByEmail(username).orElse(null)
                    : userRepository.findByPhone(username).orElse(null);
            if (current != null && !current.getId().equals(target.getId())) {
                boolean followed = userFollowRepository.existsByFollowerAndFollowee(current, target);
                dto.setIsFollowed(followed);
            } else {
                dto.setIsFollowed(false);
            }
        } else {
            dto.setIsFollowed(false);
        }

        // 偏好映射
        UserDtos.UserPublicProfile.Preference prefDto = new UserDtos.UserPublicProfile.Preference();
        if (preference != null) {
            if (preference.getTravelStyle() != null) {
                prefDto.setTravelStyles(new String[]{preference.getTravelStyle()});
            }
            if (preference.getTags() != null && !preference.getTags().isEmpty()) {
                prefDto.setInterests(preference.getTags().split(","));
            }
            if (preference.getBudgetMin() != null || preference.getBudgetMax() != null) {
                String range = String.format("¥%s - ¥%s",
                        preference.getBudgetMin() != null ? preference.getBudgetMin() : "0",
                        preference.getBudgetMax() != null ? preference.getBudgetMax() : "不限");
                prefDto.setBudgetRange(range);
            }
            if (preference.getTrafficPreference() != null) {
                prefDto.setTransportPreferences(new String[]{preference.getTrafficPreference()});
            }
        }
        dto.setPreferences(prefDto);

        // 关键统计数据：这里做一个基础实现，后续可以细化
        UserDtos.UserPublicProfile.Stats stats = new UserDtos.UserPublicProfile.Stats();
        long routeCount = tripPlanRepository.countByOwner(target);
        long noteCount = travelNoteRepository.countByAuthor(target);

        stats.setCompletedRoutes(routeCount);
        stats.setNotesCount(noteCount);
        // 结伴成功次数暂用 totalTrips 或 0 代替，根据业务可调整
        stats.setCompanionSuccessCount(reputation != null && reputation.getTotalTrips() != null
                ? reputation.getTotalTrips().longValue() : 0L);

        // 计算该用户所有内容收到的点赞 / 收藏数（基础版本：游记 + 路线 + 结伴帖子）
        java.util.List<TravelNote> userNotes = travelNoteRepository.findByAuthorOrderByCreatedAtDesc(target);
        java.util.List<TripPlan> userRoutes = tripPlanRepository.findByOwnerOrderByCreatedAtDesc(target);
        java.util.List<CompanionPost> userPosts = companionPostRepository.findByCreatorAndVisibilityOrderByCreatedAtDesc(target, "public");

        java.util.List<Long> noteIds = userNotes.stream().map(TravelNote::getId).toList();
        java.util.List<Long> routeIds = userRoutes.stream().map(TripPlan::getId).toList();
        java.util.List<Long> postIds = userPosts.stream().map(CompanionPost::getId).toList();

        long liked = 0L;
        long favorited = 0L;
        if (!noteIds.isEmpty()) {
            liked += contentLikeRepository.countByTargetTypeAndTargetIdIn("note", noteIds);
            favorited += contentFavoriteRepository.countByTargetTypeAndTargetIdIn("note", noteIds);
        }
        if (!routeIds.isEmpty()) {
            liked += contentLikeRepository.countByTargetTypeAndTargetIdIn("route", routeIds);
            favorited += contentFavoriteRepository.countByTargetTypeAndTargetIdIn("route", routeIds);
        }
        if (!postIds.isEmpty()) {
            liked += contentLikeRepository.countByTargetTypeAndTargetIdIn("companion", postIds);
            favorited += contentFavoriteRepository.countByTargetTypeAndTargetIdIn("companion", postIds);
        }
        stats.setLikedCount(liked);
        stats.setFavoritedCount(favorited);
        dto.setStats(stats);

        return ApiResponse.success(dto);
    }

    @GetMapping("/{userId}/notes")
    public ApiResponse<List<TravelNoteDtos.Summary>> listUserNotes(@PathVariable Long userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.badRequest("用户不存在"));
        List<TravelNote> notes = travelNoteRepository.findByAuthorOrderByCreatedAtDesc(target);
        List<TravelNoteDtos.Summary> list = notes.stream().map(n -> {
            TravelNoteDtos.Summary s = new TravelNoteDtos.Summary();
            s.setId(n.getId());
            s.setTitle(n.getTitle());
            s.setDestination(n.getDestination());
            s.setCoverImage(n.getCoverImage());
            s.setAuthorName(target.getEmail() != null ? target.getEmail() : target.getPhone());
            s.setCreatedAt(n.getCreatedAt());
            return s;
        }).collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/{userId}/routes")
    public ApiResponse<List<TripPlanDtos.PlanResponse>> listUserRoutes(@PathVariable Long userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.badRequest("用户不存在"));
        List<TripPlan> plans = tripPlanRepository.findByOwnerOrderByCreatedAtDesc(target);
        List<TripPlanDtos.PlanResponse> list = plans.stream().map(p -> {
            TripPlanDtos.PlanResponse dto = new TripPlanDtos.PlanResponse();
            dto.setId(p.getId());
            dto.setTitle(p.getTitle());
            dto.setDestination(p.getDestination());
            dto.setStartDate(p.getStartDate());
            dto.setEndDate(p.getEndDate());
            dto.setBudget(p.getBudget());
            dto.setPeopleCount(p.getPeopleCount());
            dto.setPace(p.getPace());
            dto.setDays(null);
            return dto;
        }).collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/{userId}/companion")
    public ApiResponse<List<CompanionDtos.PostSummary>> listUserCompanions(@PathVariable Long userId) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.badRequest("用户不存在"));
        List<CompanionPost> posts = companionPostRepository.findByCreatorAndVisibilityOrderByCreatedAtDesc(target, "public");
        List<CompanionDtos.PostSummary> list = posts.stream().map(p -> {
            CompanionDtos.PostSummary dto = new CompanionDtos.PostSummary();
            dto.setId(p.getId());
            dto.setDestination(p.getDestination());
            dto.setStartDate(p.getStartDate());
            dto.setEndDate(p.getEndDate());
            dto.setMinPeople(p.getMinPeople());
            dto.setMaxPeople(p.getMaxPeople());
            dto.setBudgetMin(p.getBudgetMin());
            dto.setBudgetMax(p.getBudgetMax());
            dto.setStatus(p.getStatus());
            dto.setCreatorNickname(null);
            dto.setRelatedPlanId(p.getRelatedPlanId());
            return dto;
        }).collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/{userId}/reviews")
    public ApiResponse<CommentDtos.PagedResult> listUserReviews(@PathVariable Long userId,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "5") int pageSize) {
        if (page < 1) page = 1;
        if (pageSize <= 0) pageSize = 5;
        // 这里约定结伴评价的 targetType 为 companion_team，真实项目可根据需要调整
        // 一个简化实现：先查出该用户作为发起人的所有结伴帖子，再取其对应的 teamId 列表作为评价目标
        User target = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.badRequest("用户不存在"));
        List<CompanionPost> posts = companionPostRepository.findByCreatorAndVisibilityOrderByCreatedAtDesc(target, "public");
        List<Long> targetIds = posts.stream().map(CompanionPost::getId).toList();
        if (targetIds.isEmpty()) {
            CommentDtos.PagedResult empty = new CommentDtos.PagedResult();
            empty.setList(List.of());
            empty.setPage(page);
            empty.setPageSize(pageSize);
            empty.setTotal(0L);
            return ApiResponse.success(empty);
        }
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page - 1, pageSize);
        var commentPage = commentRepository.findByTargetTypeAndTargetIdInOrderByCreatedAtDesc("companion", targetIds, pageable);
        CommentDtos.PagedResult result = new CommentDtos.PagedResult();
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setTotal(commentPage.getTotalElements());
        List<CommentDtos.CommentItem> items = commentPage.getContent().stream().map(c -> {
            CommentDtos.CommentItem item = new CommentDtos.CommentItem();
            item.setId(c.getId());
            item.setUserName(c.getUser() != null
                    ? (c.getUser().getEmail() != null ? c.getUser().getEmail() : c.getUser().getPhone())
                    : "旅友");
            item.setContent(c.getContent());
            item.setScore(c.getScore());
            item.setCreatedAt(c.getCreatedAt());
            if (c.getTags() != null && !c.getTags().isEmpty()) {
                item.setTags(java.util.Arrays.asList(c.getTags().split(",")));
            }
            return item;
        }).collect(Collectors.toList());
        result.setList(items);
        return ApiResponse.success(result);
    }

    @PostMapping("/{userId}/follow")
    public ApiResponse<Void> follow(@PathVariable Long userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("请先登录");
        }
        String username = auth.getName();
        User current = username.contains("@")
                ? userRepository.findByEmail(username).orElse(null)
                : userRepository.findByPhone(username).orElse(null);
        if (current == null) {
            throw BusinessException.unauthorized("用户不存在");
        }
        if (current.getId().equals(userId)) {
            throw BusinessException.badRequest("不能关注自己");
        }
        User target = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.badRequest("用户不存在"));

        if (!userFollowRepository.existsByFollowerAndFollowee(current, target)) {
            com.example.travel.user.entity.UserFollow follow = new com.example.travel.user.entity.UserFollow();
            follow.setFollower(current);
            follow.setFollowee(target);
            userFollowRepository.save(follow);
        }
        return ApiResponse.success();
    }

    @PostMapping("/{userId}/unfollow")
    public ApiResponse<Void> unfollow(@PathVariable Long userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("请先登录");
        }
        String username = auth.getName();
        User current = username.contains("@")
                ? userRepository.findByEmail(username).orElse(null)
                : userRepository.findByPhone(username).orElse(null);
        if (current == null) {
            throw BusinessException.unauthorized("用户不存在");
        }
        if (current.getId().equals(userId)) {
            throw BusinessException.badRequest("不能取消关注自己");
        }
        User target = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.badRequest("用户不存在"));

        userFollowRepository.deleteByFollowerAndFollowee(current, target);
        return ApiResponse.success();
    }
}


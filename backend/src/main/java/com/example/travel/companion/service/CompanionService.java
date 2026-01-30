package com.example.travel.companion.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.companion.dto.CompanionDtos;
import com.example.travel.companion.entity.CompanionPost;
import com.example.travel.companion.entity.CompanionTeam;
import com.example.travel.companion.entity.TeamMember;
import com.example.travel.companion.entity.TeamShare;
import com.example.travel.companion.repository.CompanionPostRepository;
import com.example.travel.companion.repository.CompanionTeamRepository;
import com.example.travel.companion.repository.TeamMemberRepository;
import com.example.travel.companion.repository.TeamShareRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.entity.UserPreference;
import com.example.travel.user.entity.UserReputation;
import com.example.travel.user.repository.UserRepository;
import com.example.travel.user.repository.UserProfileRepository;
import com.example.travel.user.repository.UserPreferenceRepository;
import com.example.travel.user.repository.UserReputationRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CompanionService {

    private final CompanionPostRepository companionPostRepository;
    private final CompanionTeamRepository companionTeamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamShareRepository teamShareRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserReputationRepository userReputationRepository;

    public CompanionService(CompanionPostRepository companionPostRepository,
                            CompanionTeamRepository companionTeamRepository,
                            TeamMemberRepository teamMemberRepository,
                            TeamShareRepository teamShareRepository,
                            UserRepository userRepository,
                            UserProfileRepository userProfileRepository,
                            UserPreferenceRepository userPreferenceRepository,
                            UserReputationRepository userReputationRepository) {
        this.companionPostRepository = companionPostRepository;
        this.companionTeamRepository = companionTeamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamShareRepository = teamShareRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.userReputationRepository = userReputationRepository;
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
    public Long publish(CompanionDtos.PublishRequest req) {
        if (req.getEndDate().isBefore(req.getStartDate())) {
            throw BusinessException.badRequest("结束日期不能早于开始日期");
        }
        User creator = getCurrentUser();
        CompanionPost post = new CompanionPost();
        post.setCreator(creator);
        post.setRelatedPlanId(req.getRelatedPlanId());
        post.setDestination(req.getDestination());
        post.setStartDate(req.getStartDate());
        post.setEndDate(req.getEndDate());
        post.setMinPeople(req.getMinPeople());
        post.setMaxPeople(req.getMaxPeople());
        post.setBudgetMin(req.getBudgetMin());
        post.setBudgetMax(req.getBudgetMax());
        post.setExpectedMateDesc(req.getExpectedMateDesc());
        if (StringUtils.hasText(req.getVisibility())) {
            post.setVisibility(req.getVisibility());
        }
        companionPostRepository.save(post);
        return post.getId();
    }

    public List<CompanionDtos.PostSummary> search(CompanionDtos.SearchRequest req) {
        String dest = StringUtils.hasText(req.getDestination()) ? req.getDestination() : "";
        LocalDate start = req.getStartDate() != null ? req.getStartDate() : LocalDate.now().minusMonths(1);
        LocalDate end = req.getEndDate() != null ? req.getEndDate() : LocalDate.now().plusYears(1);
        List<CompanionPost> posts = companionPostRepository
                .findByDestinationContainingAndStartDateGreaterThanEqualAndEndDateLessThanEqual(dest, start, end);
        return posts.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public List<CompanionDtos.PostSummary> myPosts() {
        User current = getCurrentUser();
        List<CompanionPost> posts = companionPostRepository.findByCreatorOrderByCreatedAtDesc(current);
        return posts.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public CompanionDtos.PostDetail getPostDetail(Long postId) {
        CompanionPost post = companionPostRepository.findById(postId)
                .orElseThrow(() -> BusinessException.badRequest("结伴信息不存在"));
        // 复用 summary 的“昵称/头像/信誉/标签”填充逻辑，避免详情页显示邮箱
        CompanionDtos.PostSummary summary = toSummary(post);
        CompanionDtos.PostDetail detail = new CompanionDtos.PostDetail();
        detail.setId(summary.getId());
        detail.setDestination(summary.getDestination());
        detail.setStartDate(summary.getStartDate());
        detail.setEndDate(summary.getEndDate());
        detail.setMinPeople(summary.getMinPeople());
        detail.setMaxPeople(summary.getMaxPeople());
        detail.setBudgetMin(summary.getBudgetMin());
        detail.setBudgetMax(summary.getBudgetMax());
        detail.setStatus(summary.getStatus());
        detail.setCreatorId(summary.getCreatorId());
        detail.setCreatorNickname(summary.getCreatorNickname());
        detail.setCreatorAvatar(summary.getCreatorAvatar());
        detail.setCreatorReputationLevel(summary.getCreatorReputationLevel());
        detail.setCreatorTags(summary.getCreatorTags());
        detail.setRelatedPlanId(summary.getRelatedPlanId());
        detail.setExpectedMateDesc(post.getExpectedMateDesc());
        companionTeamRepository.findFirstByPostOrderByIdAsc(post)
                .ifPresent(team -> detail.setTeamId(team.getId()));
        return detail;
    }

    public CompanionDtos.TeamDetail getTeamDetail(Long teamId) {
        CompanionTeam team = companionTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.badRequest("小队不存在"));
        CompanionPost post = team.getPost();
        CompanionDtos.TeamDetail detail = new CompanionDtos.TeamDetail();
        detail.setId(team.getId());
        detail.setName(team.getName());
        detail.setStatus(team.getStatus());
        if (post != null) {
            detail.setPostId(post.getId());
            detail.setDestination(post.getDestination());
            detail.setStartDate(post.getStartDate());
            detail.setEndDate(post.getEndDate());
            detail.setRelatedPlanId(post.getRelatedPlanId());
            detail.setMaxPeople(post.getMaxPeople());
            detail.setBudgetMin(post.getBudgetMin());
            detail.setBudgetMax(post.getBudgetMax());
        }
        List<TeamMember> members = teamMemberRepository.findByTeam(team);
        List<CompanionDtos.TeamMemberItem> items = members.stream().map(m -> {
            CompanionDtos.TeamMemberItem item = new CompanionDtos.TeamMemberItem();
            item.setUserId(m.getUser().getId());
            UserProfile profile = userProfileRepository.findById(m.getUser().getId()).orElse(null);
            Long uid = m.getUser().getId();
            String displayName = "用户" + uid;
            if (profile != null && profile.getNickname() != null && !profile.getNickname().isBlank()) {
                displayName = profile.getNickname();
            } else if (m.getUser().getPhone() != null && !m.getUser().getPhone().isBlank()) {
                displayName = m.getUser().getPhone();
            }
            // 无昵称且无手机号时显示「用户{id}」，保证每个成员有唯一标识
            item.setUserName(displayName);
            item.setAvatar(profile != null ? profile.getAvatar() : null);
            UserReputation reputation = userReputationRepository.findById(m.getUser().getId()).orElse(null);
            if (reputation != null && reputation.getLevel() != null) {
                item.setReputationLevel(reputation.getLevel());
            }
            item.setRole(m.getRole());
            item.setState(m.getState());
            return item;
        }).collect(Collectors.toList());
        detail.setMembers(items);
        return detail;
    }

    private CompanionDtos.PostSummary toSummary(CompanionPost post) {
        CompanionDtos.PostSummary dto = new CompanionDtos.PostSummary();
        dto.setId(post.getId());
        dto.setDestination(post.getDestination());
        dto.setStartDate(post.getStartDate());
        dto.setEndDate(post.getEndDate());
        dto.setMinPeople(post.getMinPeople());
        dto.setMaxPeople(post.getMaxPeople());
        dto.setBudgetMin(post.getBudgetMin());
        dto.setBudgetMax(post.getBudgetMax());
        dto.setStatus(post.getStatus());
        if (post.getCreator() != null) {
            dto.setCreatorId(post.getCreator().getId());
            // 获取用户昵称和头像
            UserProfile profile = userProfileRepository.findById(post.getCreator().getId()).orElse(null);
            if (profile != null) {
                if (profile.getNickname() != null && !profile.getNickname().isBlank()) {
                    dto.setCreatorNickname(profile.getNickname());
                } else {
                    dto.setCreatorNickname("旅人" + post.getCreator().getId());
                }
                dto.setCreatorAvatar(profile.getAvatar());
            } else {
                dto.setCreatorNickname("旅人" + post.getCreator().getId());
                dto.setCreatorAvatar(null);
            }
            // 获取用户信誉等级
            UserReputation reputation = userReputationRepository.findById(post.getCreator().getId()).orElse(null);
            if (reputation != null && reputation.getLevel() != null) {
                dto.setCreatorReputationLevel(reputation.getLevel());
            }
            // 获取用户标签
            UserPreference preference = userPreferenceRepository.findById(post.getCreator().getId()).orElse(null);
            if (preference != null && preference.getTags() != null && !preference.getTags().isEmpty()) {
                dto.setCreatorTags(preference.getTags());
            }
        } else {
            dto.setCreatorId(null);
            dto.setCreatorNickname("");
            dto.setCreatorAvatar(null);
        }
        dto.setRelatedPlanId(post.getRelatedPlanId());
        return dto;
    }

    @Transactional
    public Long createTeam(Long postId) {
        User current = getCurrentUser();
        CompanionPost post = companionPostRepository.findById(postId)
                .orElseThrow(() -> BusinessException.badRequest("结伴信息不存在"));

        CompanionTeam team = new CompanionTeam();
        team.setPost(post);
        team.setName(post.getDestination() + " 小队");
        team.setStatus("forming");
        companionTeamRepository.save(team);

        TeamMember leader = new TeamMember();
        leader.setTeam(team);
        leader.setUser(current);
        leader.setRole("leader");
        leader.setState("joined");
        teamMemberRepository.save(leader);

        return team.getId();
    }

    @Transactional
    public void joinTeam(Long teamId) {
        User current = getCurrentUser();
        CompanionTeam team = companionTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.badRequest("小队不存在"));
        if ("disbanded".equals(team.getStatus())) {
            throw BusinessException.badRequest("小队已解散");
        }

        boolean exists = teamMemberRepository.findByTeamAndUser(team, current).isPresent();
        if (exists) {
            return;
        }

        TeamMember member = new TeamMember();
        member.setTeam(team);
        member.setUser(current);
        member.setRole("member");
        member.setState("joined");
        teamMemberRepository.save(member);
    }

    /**
     * 退出小队（仅普通成员；队长请使用解散或转让队长）
     */
    @Transactional
    public void quitTeam(Long teamId) {
        User current = getCurrentUser();
        CompanionTeam team = companionTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.badRequest("小队不存在"));
        TeamMember membership = teamMemberRepository.findByTeamAndUser(team, current)
                .orElseThrow(() -> BusinessException.badRequest("您不是该小队成员"));
        if ("leader".equals(membership.getRole())) {
            throw BusinessException.badRequest("队长请先解散小队或转让队长后再退出");
        }
        teamMemberRepository.delete(membership);
    }

    /**
     * 解散小队（仅队长）
     */
    @Transactional
    public void dissolveTeam(Long teamId) {
        User current = getCurrentUser();
        CompanionTeam team = companionTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.badRequest("小队不存在"));
        TeamMember leader = teamMemberRepository.findByTeamAndUser(team, current)
                .orElseThrow(() -> BusinessException.forbidden("仅队长可解散小队"));
        if (!"leader".equals(leader.getRole())) {
            throw BusinessException.forbidden("仅队长可解散小队");
        }
        team.setStatus("disbanded");
        companionTeamRepository.save(team);
    }

    /**
     * 队长移除指定成员
     */
    @Transactional
    public void removeMember(Long teamId, Long userId) {
        User current = getCurrentUser();
        CompanionTeam team = companionTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.badRequest("小队不存在"));
        TeamMember leader = teamMemberRepository.findByTeamAndUser(team, current)
                .orElseThrow(() -> BusinessException.forbidden("仅队长可移除成员"));
        if (!"leader".equals(leader.getRole())) {
            throw BusinessException.forbidden("仅队长可移除成员");
        }
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.badRequest("目标用户不存在"));
        TeamMember toRemove = teamMemberRepository.findByTeamAndUser(team, targetUser)
                .orElseThrow(() -> BusinessException.badRequest("该用户不是小队成员"));
        if ("leader".equals(toRemove.getRole())) {
            throw BusinessException.badRequest("不能移除队长，请先转让队长或解散小队");
        }
        teamMemberRepository.delete(toRemove);
    }

    /**
     * 队长将小队/行程分享给指定用户，被分享人可查看该行程
     */
    @Transactional
    public void shareTeam(Long teamId, Long toUserId) {
        User current = getCurrentUser();
        CompanionTeam team = companionTeamRepository.findById(teamId)
                .orElseThrow(() -> BusinessException.badRequest("小队不存在"));
        TeamMember leader = teamMemberRepository.findByTeamAndUser(team, current)
                .orElseThrow(() -> BusinessException.forbidden("仅队长可分享小队"));
        if (!"leader".equals(leader.getRole())) {
            throw BusinessException.forbidden("仅队长可分享小队");
        }
        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> BusinessException.badRequest("目标用户不存在"));
        if (teamShareRepository.existsByTeamAndToUser(team, toUser)) {
            return;
        }
        TeamShare share = new TeamShare();
        share.setTeam(team);
        share.setToUser(toUser);
        teamShareRepository.save(share);
    }

    /**
     * 智能推荐结伴活动
     * 如果用户已登录，根据用户标签推荐；否则推荐热度最高的
     */
    public List<CompanionDtos.PostSummary> recommend(int limit) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() 
                && !"anonymousUser".equals(auth.getPrincipal());

        if (isAuthenticated) {
            // 已登录：根据用户标签智能推荐
            return recommendByUserTags(limit);
        } else {
            // 未登录：推荐热度最高的
            return recommendByPopularity(limit);
        }
    }

    /**
     * 根据用户标签推荐
     */
    private List<CompanionDtos.PostSummary> recommendByUserTags(int limit) {
        try {
            User current = getCurrentUser();
            UserPreference preference = userPreferenceRepository.findById(current.getId()).orElse(null);
            
            if (preference == null || preference.getTags() == null || preference.getTags().isEmpty()) {
                // 如果没有偏好设置，降级为热度推荐
                return recommendByPopularity(limit);
            }

            // 获取用户标签列表
            String[] userTags = preference.getTags().split(",");
            Set<String> userTagSet = Arrays.stream(userTags)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

            if (userTagSet.isEmpty()) {
                return recommendByPopularity(limit);
            }

            // 获取所有公开的结伴活动（按日期范围）
            LocalDate now = LocalDate.now();
            List<CompanionPost> allPosts = companionPostRepository
                    .findByDestinationContainingAndStartDateGreaterThanEqualAndEndDateLessThanEqual(
                            "", now.minusMonths(1), now.plusYears(1));
            if (allPosts.isEmpty()) {
                allPosts = companionPostRepository.findTop20ByVisibilityOrderByCreatedAtDesc("public");
            }

            // 计算每个活动的匹配分数
            List<CompanionPostWithScore> scoredPosts = allPosts.stream()
                    .filter(post -> post.getCreator() != null && !post.getCreator().getId().equals(current.getId()))
                    .map(post -> {
                        int score = calculateMatchScore(post, userTagSet, preference);
                        return new CompanionPostWithScore(post, score);
                    })
                    .filter(item -> item.score > 0) // 至少有一个标签匹配
                    .sorted((a, b) -> Integer.compare(b.score, a.score)) // 按分数降序
                    .limit(limit)
                    .collect(Collectors.toList());

            return scoredPosts.stream()
                    .map(item -> toSummary(item.post))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            // 如果出错，降级为热度推荐
            return recommendByPopularity(limit);
        }
    }

    /**
     * 根据热度推荐（信誉分 + 完成行程次数）
     * 若按日期范围查询无结果，则兜底返回最近创建的公开结伴帖
     */
    private List<CompanionDtos.PostSummary> recommendByPopularity(int limit) {
        LocalDate now = LocalDate.now();
        List<CompanionPost> allPosts = companionPostRepository
                .findByDestinationContainingAndStartDateGreaterThanEqualAndEndDateLessThanEqual(
                        "", now.minusMonths(1), now.plusYears(1));

        // 无结果时兜底：取最近创建的公开帖
        if (allPosts.isEmpty()) {
            allPosts = companionPostRepository.findTop20ByVisibilityOrderByCreatedAtDesc("public");
        }

        // 计算每个活动的热度分数
        List<CompanionPostWithScore> scoredPosts = allPosts.stream()
                .filter(post -> post.getCreator() != null)
                .map(post -> {
                    int score = calculatePopularityScore(post);
                    return new CompanionPostWithScore(post, score);
                })
                .sorted((a, b) -> Integer.compare(b.score, a.score)) // 按分数降序
                .limit(limit)
                .collect(Collectors.toList());

        return scoredPosts.stream()
                .map(item -> toSummary(item.post))
                .collect(Collectors.toList());
    }

    /**
     * 计算匹配分数（基于标签匹配）
     */
    private int calculateMatchScore(CompanionPost post, Set<String> userTags, UserPreference preference) {
        int score = 0;
        
        // 获取创建者的偏好标签
        User creator = post.getCreator();
        if (creator != null) {
            UserPreference creatorPref = userPreferenceRepository.findById(creator.getId()).orElse(null);
            if (creatorPref != null && creatorPref.getTags() != null && !creatorPref.getTags().isEmpty()) {
                String[] creatorTags = creatorPref.getTags().split(",");
                for (String tag : creatorTags) {
                    String trimmedTag = tag.trim();
                    if (userTags.contains(trimmedTag)) {
                        score += 10; // 每个匹配的标签加10分
                    }
                }
            }
        }

        // 如果旅行风格匹配，额外加分
        if (preference.getTravelStyle() != null && creator != null) {
            UserPreference creatorPref = userPreferenceRepository.findById(creator.getId()).orElse(null);
            if (creatorPref != null && preference.getTravelStyle().equals(creatorPref.getTravelStyle())) {
                score += 20; // 旅行风格匹配加20分
            }
        }

        // 信誉分作为加分项
        if (creator != null) {
            UserReputation reputation = userReputationRepository.findById(creator.getId()).orElse(null);
            if (reputation != null && reputation.getScore() != null) {
                score += reputation.getScore() / 10; // 信誉分除以10作为加分
            }
        }

        return score;
    }

    /**
     * 计算热度分数（信誉分 + 完成行程次数）
     */
    private int calculatePopularityScore(CompanionPost post) {
        int score = 0;
        User creator = post.getCreator();
        
        if (creator != null) {
            UserReputation reputation = userReputationRepository.findById(creator.getId()).orElse(null);
            if (reputation != null) {
                // 信誉分权重：1分 = 1分
                if (reputation.getScore() != null) {
                    score += reputation.getScore();
                }
                // 完成行程次数权重：1次 = 10分
                if (reputation.getTotalTrips() != null) {
                    score += reputation.getTotalTrips() * 10;
                }
                // 好评次数权重：1次 = 5分
                if (reputation.getPositiveCount() != null) {
                    score += reputation.getPositiveCount() * 5;
                }
            }
        }

        return score;
    }

    /**
     * 内部类：用于排序的结伴活动与分数
     */
    private static class CompanionPostWithScore {
        final CompanionPost post;
        final int score;

        CompanionPostWithScore(CompanionPost post, int score) {
            this.post = post;
            this.score = score;
        }
    }
}


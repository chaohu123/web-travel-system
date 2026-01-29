package com.example.travel.social.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.social.dto.CommentDtos;
import com.example.travel.social.entity.Comment;
import com.example.travel.social.repository.CommentRepository;
import com.example.travel.social.repository.TravelNoteRepository;
import com.example.travel.companion.entity.CompanionTeam;
import com.example.travel.companion.entity.TeamMember;
import com.example.travel.companion.repository.CompanionTeamRepository;
import com.example.travel.companion.repository.TeamMemberRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.entity.UserReputation;
import com.example.travel.user.repository.UserProfileRepository;
import com.example.travel.user.repository.UserReputationRepository;
import com.example.travel.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TravelNoteRepository travelNoteRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserReputationRepository userReputationRepository;
    private final CompanionTeamRepository companionTeamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public CommentService(CommentRepository commentRepository,
                          TravelNoteRepository travelNoteRepository,
                          UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          UserReputationRepository userReputationRepository,
                          CompanionTeamRepository companionTeamRepository,
                          TeamMemberRepository teamMemberRepository) {
        this.commentRepository = commentRepository;
        this.travelNoteRepository = travelNoteRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userReputationRepository = userReputationRepository;
        this.companionTeamRepository = companionTeamRepository;
        this.teamMemberRepository = teamMemberRepository;
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
    public void create(CommentDtos.CreateRequest req) {
        // 对 note 做存在性校验示例，其它类型可在以后扩展
        if ("note".equals(req.getTargetType())) {
            boolean exists = travelNoteRepository.existsById(req.getTargetId());
            if (!exists) {
                throw BusinessException.badRequest("游记不存在");
            }
        }

        User user = getCurrentUser();
        Comment comment = new Comment();
        comment.setUser(user);
        comment.setTargetType(req.getTargetType());
        comment.setTargetId(req.getTargetId());
        comment.setContent(req.getContent());
        comment.setScore(req.getScore());
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            String tagsJoined = String.join(",", req.getTags());
            comment.setTags(tagsJoined);
        }
        commentRepository.save(comment);

        // 如果是针对小队的评分，则给队长增加信誉积分
        if ("companion_team".equals(req.getTargetType()) && req.getScore() != null) {
            CompanionTeam team = companionTeamRepository.findById(req.getTargetId())
                    .orElseThrow(() -> BusinessException.badRequest("小队不存在"));
            TeamMember leader = teamMemberRepository.findByTeam(team).stream()
                    .filter(m -> "leader".equalsIgnoreCase(m.getRole()))
                    .findFirst()
                    .orElse(null);
            if (leader != null && leader.getUser() != null) {
                int scoreDelta = req.getScore() - 3; // 高于3分加分，低于3分减分
                boolean positive = req.getScore() >= 4;
                updateReputationForUser(leader.getUser().getId(), scoreDelta, positive);
            }
        }
    }

    public List<CommentDtos.CommentItem> list(String targetType, Long targetId) {
        List<Comment> list = commentRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId);
        return list.stream().map(this::toItem).collect(Collectors.toList());
    }

    private CommentDtos.CommentItem toItem(Comment comment) {
        CommentDtos.CommentItem item = new CommentDtos.CommentItem();
        item.setId(comment.getId());
        if (comment.getUser() != null) {
            item.setUserId(comment.getUser().getId());
            // 获取用户昵称，如果没有则使用邮箱或手机号
            UserProfile profile = userProfileRepository.findById(comment.getUser().getId()).orElse(null);
            if (profile != null && profile.getNickname() != null && !profile.getNickname().isBlank()) {
                item.setUserName(profile.getNickname());
            } else {
                String name = comment.getUser().getEmail() != null
                        ? comment.getUser().getEmail()
                        : (comment.getUser().getPhone() != null ? comment.getUser().getPhone() : "旅友");
                item.setUserName(name);
            }
        } else {
            item.setUserId(null);
            item.setUserName("旅友");
        }
        item.setContent(comment.getContent());
        item.setScore(comment.getScore());
        item.setCreatedAt(comment.getCreatedAt());
        if (comment.getTags() != null && !comment.getTags().isEmpty()) {
            String[] arr = comment.getTags().split(",");
            item.setTags(java.util.Arrays.asList(arr));
        }
        return item;
    }

    // 信誉更新
    private void updateReputationForUser(Long userId, int scoreDelta, boolean positive) {
        Optional<UserReputation> opt = userReputationRepository.findById(userId);
        UserReputation rep = opt.orElseGet(() -> {
            UserReputation r = new UserReputation();
            User u = userRepository.findById(userId).orElseThrow(() -> BusinessException.badRequest("用户不存在"));
            r.setUser(u);
            r.setScore(0);
            r.setLevel(1);
            r.setTotalTrips(0);
            r.setPositiveCount(0);
            return r;
        });
        rep.setScore((rep.getScore() == null ? 0 : rep.getScore()) + scoreDelta);
        rep.setTotalTrips((rep.getTotalTrips() == null ? 0 : rep.getTotalTrips()) + 1);
        if (positive) {
            rep.setPositiveCount((rep.getPositiveCount() == null ? 0 : rep.getPositiveCount()) + 1);
        }
        int currentScore = rep.getScore() == null ? 0 : rep.getScore();
        rep.setLevel(Math.max(1, currentScore / 20 + 1));
        userReputationRepository.save(rep);
    }
}


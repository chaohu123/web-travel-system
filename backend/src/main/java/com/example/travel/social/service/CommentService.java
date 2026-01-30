package com.example.travel.social.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.social.dto.CommentDtos;
import com.example.travel.social.entity.Comment;
import com.example.travel.social.entity.InteractionMessage;
import com.example.travel.social.entity.TravelNote;
import com.example.travel.social.repository.CommentRepository;
import com.example.travel.social.repository.ContentLikeRepository;
import com.example.travel.social.repository.TravelNoteRepository;
import com.example.travel.social.repository.InteractionMessageRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final ContentLikeRepository contentLikeRepository;
    private final TravelNoteRepository travelNoteRepository;
    private final InteractionMessageRepository interactionMessageRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserReputationRepository userReputationRepository;
    private final CompanionTeamRepository companionTeamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public CommentService(CommentRepository commentRepository,
                          ContentLikeRepository contentLikeRepository,
                          TravelNoteRepository travelNoteRepository,
                          InteractionMessageRepository interactionMessageRepository,
                          UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          UserReputationRepository userReputationRepository,
                          CompanionTeamRepository companionTeamRepository,
                          TeamMemberRepository teamMemberRepository) {
        this.commentRepository = commentRepository;
        this.contentLikeRepository = contentLikeRepository;
        this.travelNoteRepository = travelNoteRepository;
        this.interactionMessageRepository = interactionMessageRepository;
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

    /** 获取当前登录用户，未登录返回 null */
    private User getCurrentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String username = auth.getName();
        return (username.contains("@")
                ? userRepository.findByEmail(username)
                : userRepository.findByPhone(username))
                .orElse(null);
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

        // 给游记作者发送一条评论互动消息（目前仅对 note 生效）
        createCommentMessageIfNeeded(user, req.getTargetType(), req.getTargetId(), req.getContent());

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

    private void createCommentMessageIfNeeded(User fromUser, String targetType, Long targetId, String content) {
        if (!"note".equalsIgnoreCase(targetType)) {
            return; // 目前仅对游记评论生成互动消息
        }
        TravelNote note = travelNoteRepository.findById(targetId).orElse(null);
        if (note == null || note.getAuthor() == null || note.getAuthor().getId().equals(fromUser.getId())) {
            return;
        }
        InteractionMessage msg = new InteractionMessage();
        msg.setRecipient(note.getAuthor());
        msg.setFromUser(fromUser);
        msg.setType("COMMENT");
        msg.setTargetType("note");
        msg.setTargetId(targetId);
        msg.setTargetTitle(note.getTitle());
        String preview = content != null && content.length() > 50 ? content.substring(0, 50) + "…" : content;
        msg.setContentPreview(preview);
        interactionMessageRepository.save(msg);
    }

    @Transactional(readOnly = true)
    public List<CommentDtos.CommentItem> list(String targetType, Long targetId) {
        List<Comment> list = commentRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId);
        User currentUser = getCurrentUserOrNull();
        return list.stream().map(c -> toItem(c, currentUser)).collect(Collectors.toList());
    }

    @Transactional
    public void delete(Long id) {
        User current = getCurrentUser();
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> BusinessException.badRequest("评论不存在"));
        if (comment.getUser() == null || !comment.getUser().getId().equals(current.getId())) {
            throw BusinessException.forbidden("只能删除自己的评论");
        }
        commentRepository.delete(comment);
    }

    private CommentDtos.CommentItem toItem(Comment comment, User currentUser) {
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
        // 评论点赞数（targetType=comment, targetId=comment.id）
        long likeCount = contentLikeRepository.countByTargetTypeAndTargetId("comment", comment.getId());
        item.setLikeCount(likeCount);
        item.setLikedByCurrentUser(currentUser != null
                && contentLikeRepository.existsByUserAndTargetTypeAndTargetId(currentUser, "comment", comment.getId()));
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


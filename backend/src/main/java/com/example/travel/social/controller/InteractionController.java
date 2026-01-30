package com.example.travel.social.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.common.exception.BusinessException;
import com.example.travel.route.entity.TripPlan;
import com.example.travel.route.repository.TripPlanRepository;
import com.example.travel.social.entity.ContentFavorite;
import com.example.travel.social.entity.ContentLike;
import com.example.travel.social.entity.InteractionMessage;
import com.example.travel.social.entity.TravelNote;
import com.example.travel.social.repository.CommentRepository;
import com.example.travel.social.repository.ContentFavoriteRepository;
import com.example.travel.social.repository.ContentLikeRepository;
import com.example.travel.social.repository.InteractionMessageRepository;
import com.example.travel.social.repository.TravelNoteRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private final ContentLikeRepository likeRepository;
    private final ContentFavoriteRepository favoriteRepository;
    private final CommentRepository commentRepository;
    private final InteractionMessageRepository interactionMessageRepository;
    private final TravelNoteRepository travelNoteRepository;
    private final TripPlanRepository tripPlanRepository;
    private final UserRepository userRepository;

    public InteractionController(ContentLikeRepository likeRepository,
                                 ContentFavoriteRepository favoriteRepository,
                                 CommentRepository commentRepository,
                                 InteractionMessageRepository interactionMessageRepository,
                                 TravelNoteRepository travelNoteRepository,
                                 TripPlanRepository tripPlanRepository,
                                 UserRepository userRepository) {
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
        this.commentRepository = commentRepository;
        this.interactionMessageRepository = interactionMessageRepository;
        this.travelNoteRepository = travelNoteRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("请先登录");
        }
        String username = auth.getName();
        return (username.contains("@")
                ? userRepository.findByEmail(username)
                : userRepository.findByPhone(username))
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在"));
    }

    @Data
    public static class InteractionRequest {
        @NotBlank
        private String targetType;
        @NotNull
        private Long targetId;
    }

    @Data
    public static class InteractionSummary {
        private Long likeCount;
        private Long favoriteCount;
        private Boolean likedByCurrentUser;
        private Boolean favoritedByCurrentUser;
    }

    @PostMapping("/likes")
    public ApiResponse<Void> like(@RequestBody InteractionRequest req) {
        User user = getCurrentUser();
        String targetType = req.getTargetType();
        Long targetId = req.getTargetId();
        if ("comment".equals(targetType) && !commentRepository.existsById(targetId)) {
            throw BusinessException.badRequest("评论不存在");
        }
        if (!likeRepository.existsByUserAndTargetTypeAndTargetId(user, targetType, targetId)) {
            ContentLike like = new ContentLike();
            like.setUser(user);
            like.setTargetType(targetType);
            like.setTargetId(targetId);
            likeRepository.save(like);

            // 为游记/路线点赞创建互动消息
            createLikeMessageIfNeeded(user, targetType, targetId);
        }
        return ApiResponse.success();
    }

    /**
     * 点赞游记或路线时，为内容作者创建一条互动消息。
     * 为了避免用户对同一篇内容反复点赞 / 取消导致消息中心出现多条重复点赞记录，
     * 这里在插入新消息前，会先删除该发送人对该内容的历史 LIKE 消息，仅保留最新一条。
     */
    private void createLikeMessageIfNeeded(User fromUser, String targetType, Long targetId) {
        User recipient = null;
        String title = null;
        if ("note".equalsIgnoreCase(targetType)) {
            TravelNote note = travelNoteRepository.findById(targetId).orElse(null);
            if (note != null && note.getAuthor() != null
                    && !note.getAuthor().getId().equals(fromUser.getId())) {
                recipient = note.getAuthor();
                title = note.getTitle();
            }
        } else if ("route".equalsIgnoreCase(targetType)) {
            TripPlan plan = tripPlanRepository.findById(targetId).orElse(null);
            if (plan != null && plan.getOwner() != null
                    && !plan.getOwner().getId().equals(fromUser.getId())) {
                recipient = plan.getOwner();
                title = plan.getTitle();
            }
        }
        if (recipient == null) {
            return; // 自己给自己点赞或目标不存在，不产生消息
        }

        // 先删除同一发送人 + 同一内容的历史点赞消息，只保留最新一次
        interactionMessageRepository.deleteByRecipientAndFromUserAndTypeAndTargetTypeAndTargetId(
                recipient,
                fromUser,
                "LIKE",
                targetType.toLowerCase(),
                targetId
        );

        InteractionMessage msg = new InteractionMessage();
        msg.setRecipient(recipient);
        msg.setFromUser(fromUser);
        msg.setType("LIKE");
        msg.setTargetType(targetType.toLowerCase());
        msg.setTargetId(targetId);
        msg.setTargetTitle(title);
        msg.setContentPreview(null);
        interactionMessageRepository.save(msg);
    }

    @DeleteMapping("/likes")
    @Transactional
    public ApiResponse<Void> unlike(@RequestParam String targetType,
                                    @RequestParam Long targetId) {
        User user = getCurrentUser();
        likeRepository.deleteByUserAndTargetTypeAndTargetId(user, targetType, targetId);
        return ApiResponse.success();
    }

    @PostMapping("/favorites")
    public ApiResponse<Void> favorite(@RequestBody InteractionRequest req) {
        User user = getCurrentUser();
        if (!favoriteRepository.existsByUserAndTargetTypeAndTargetId(user, req.getTargetType(), req.getTargetId())) {
            ContentFavorite fav = new ContentFavorite();
            fav.setUser(user);
            fav.setTargetType(req.getTargetType());
            fav.setTargetId(req.getTargetId());
            favoriteRepository.save(fav);
        }
        return ApiResponse.success();
    }

    @DeleteMapping("/favorites")
    @Transactional
    public ApiResponse<Void> unfavorite(@RequestParam String targetType,
                                        @RequestParam Long targetId) {
        User user = getCurrentUser();
        favoriteRepository.deleteByUserAndTargetTypeAndTargetId(user, targetType, targetId);
        return ApiResponse.success();
    }

    /** 当前用户收藏列表（按收藏时间倒序） */
    @GetMapping("/favorites")
    public ApiResponse<List<FavoriteItemResponse>> myFavorites() {
        User user = getCurrentUser();
        List<ContentFavorite> list = favoriteRepository.findByUserOrderByCreatedAtDesc(user);
        List<FavoriteItemResponse> result = list.stream()
                .map(fav -> {
                    FavoriteItemResponse dto = new FavoriteItemResponse();
                    dto.setTargetType(fav.getTargetType());
                    dto.setTargetId(fav.getTargetId());
                    return dto;
                })
                .toList();
        return ApiResponse.success(result);
    }

    @Data
    public static class FavoriteItemResponse {
        private String targetType;
        private Long targetId;
    }

    @GetMapping("/summary")
    public ApiResponse<InteractionSummary> summary(@RequestParam String targetType,
                                                   @RequestParam Long targetId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = null;
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();
            currentUser = (username.contains("@")
                    ? userRepository.findByEmail(username)
                    : userRepository.findByPhone(username))
                    .orElse(null);
        }

        long likeCount = likeRepository.countByTargetTypeAndTargetId(targetType, targetId);
        long favoriteCount = favoriteRepository.countByTargetTypeAndTargetId(targetType, targetId);

        InteractionSummary summary = new InteractionSummary();
        summary.setLikeCount(likeCount);
        summary.setFavoriteCount(favoriteCount);
        if (currentUser != null) {
            summary.setLikedByCurrentUser(
                    likeRepository.existsByUserAndTargetTypeAndTargetId(currentUser, targetType, targetId)
            );
            summary.setFavoritedByCurrentUser(
                    favoriteRepository.existsByUserAndTargetTypeAndTargetId(currentUser, targetType, targetId)
            );
        } else {
            summary.setLikedByCurrentUser(false);
            summary.setFavoritedByCurrentUser(false);
        }
        return ApiResponse.success(summary);
    }
}


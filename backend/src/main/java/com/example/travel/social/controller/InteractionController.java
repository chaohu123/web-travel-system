package com.example.travel.social.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.common.exception.BusinessException;
import com.example.travel.social.entity.ContentFavorite;
import com.example.travel.social.entity.ContentLike;
import com.example.travel.social.repository.ContentFavoriteRepository;
import com.example.travel.social.repository.ContentLikeRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private final ContentLikeRepository likeRepository;
    private final ContentFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;

    public InteractionController(ContentLikeRepository likeRepository,
                                 ContentFavoriteRepository favoriteRepository,
                                 UserRepository userRepository) {
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
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
        if (!likeRepository.existsByUserAndTargetTypeAndTargetId(user, req.getTargetType(), req.getTargetId())) {
            ContentLike like = new ContentLike();
            like.setUser(user);
            like.setTargetType(req.getTargetType());
            like.setTargetId(req.getTargetId());
            likeRepository.save(like);
        }
        return ApiResponse.success();
    }

    @DeleteMapping("/likes")
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
    public ApiResponse<Void> unfavorite(@RequestParam String targetType,
                                        @RequestParam Long targetId) {
        User user = getCurrentUser();
        favoriteRepository.deleteByUserAndTargetTypeAndTargetId(user, targetType, targetId);
        return ApiResponse.success();
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


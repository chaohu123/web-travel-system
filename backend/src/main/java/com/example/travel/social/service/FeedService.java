package com.example.travel.social.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.social.dto.FeedDtos;
import com.example.travel.social.entity.FeedPost;
import com.example.travel.social.repository.FeedPostRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.repository.UserProfileRepository;
import com.example.travel.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private final FeedPostRepository feedPostRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public FeedService(FeedPostRepository feedPostRepository,
                       UserRepository userRepository,
                       UserProfileRepository userProfileRepository) {
        this.feedPostRepository = feedPostRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
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
    public Long create(FeedDtos.CreateRequest req) {
        User user = getCurrentUser();
        FeedPost post = new FeedPost();
        post.setUser(user);
        post.setContent(req.getContent());
        post.setImageUrlsJson(req.getImageUrlsJson());
        feedPostRepository.save(post);
        return post.getId();
    }

    public List<FeedDtos.FeedItem> list() {
        return feedPostRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toItem)
                .collect(Collectors.toList());
    }

    private FeedDtos.FeedItem toItem(FeedPost post) {
        FeedDtos.FeedItem item = new FeedDtos.FeedItem();
        item.setId(post.getId());
        item.setContent(post.getContent());
        item.setImageUrlsJson(post.getImageUrlsJson());
        if (post.getUser() != null) {
            item.setAuthorId(post.getUser().getId());
            item.setAuthorName(resolveAuthorName(post.getUser()));
        } else {
            item.setAuthorName("用户");
        }
        item.setCreatedAt(post.getCreatedAt());
        return item;
    }

    /** 优先使用昵称，无昵称时回退到邮箱/手机号 */
    private String resolveAuthorName(User user) {
        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        if (profile != null && profile.getNickname() != null && !profile.getNickname().isBlank()) {
            return profile.getNickname();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return user.getPhone() != null ? user.getPhone() : "用户";
    }
}


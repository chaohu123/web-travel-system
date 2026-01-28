package com.example.travel.social.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.social.dto.FeedDtos;
import com.example.travel.social.entity.FeedPost;
import com.example.travel.social.repository.FeedPostRepository;
import com.example.travel.user.entity.User;
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

    public FeedService(FeedPostRepository feedPostRepository,
                       UserRepository userRepository) {
        this.feedPostRepository = feedPostRepository;
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
        item.setAuthorName(post.getUser() != null ? post.getUser().getEmail() : "");
        item.setCreatedAt(post.getCreatedAt());
        return item;
    }
}


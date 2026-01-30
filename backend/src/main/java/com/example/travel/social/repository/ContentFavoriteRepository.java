package com.example.travel.social.repository;

import com.example.travel.social.entity.ContentFavorite;
import com.example.travel.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentFavoriteRepository extends JpaRepository<ContentFavorite, Long> {

    long countByTargetTypeAndTargetIdIn(String targetType, List<Long> targetIds);

    long countByTargetTypeAndTargetId(String targetType, Long targetId);

    boolean existsByUserAndTargetTypeAndTargetId(User user, String targetType, Long targetId);

    void deleteByUserAndTargetTypeAndTargetId(User user, String targetType, Long targetId);

    List<ContentFavorite> findByUserOrderByCreatedAtDesc(User user);
}


package com.example.travel.user.repository;

import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    long countByFollowee(User followee);

    long countByFollower(User follower);

    boolean existsByFollowerAndFollowee(User follower, User followee);

    void deleteByFollowerAndFollowee(User follower, User followee);

    java.util.List<UserFollow> findByFollowerOrderByCreatedAtDesc(User follower);
}


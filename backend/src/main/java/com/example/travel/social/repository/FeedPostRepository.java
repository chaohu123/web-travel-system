package com.example.travel.social.repository;

import com.example.travel.social.entity.FeedPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedPostRepository extends JpaRepository<FeedPost, Long> {

    List<FeedPost> findAllByOrderByCreatedAtDesc();
}


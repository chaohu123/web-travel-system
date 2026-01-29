package com.example.travel.social.repository;

import com.example.travel.social.entity.Comment;
import com.example.travel.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(String targetType, Long targetId);

    Page<Comment> findByTargetTypeAndTargetIdInOrderByCreatedAtDesc(String targetType, List<Long> targetIds, Pageable pageable);
}


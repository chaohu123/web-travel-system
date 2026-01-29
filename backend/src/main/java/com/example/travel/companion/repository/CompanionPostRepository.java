package com.example.travel.companion.repository;

import com.example.travel.companion.entity.CompanionPost;
import com.example.travel.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long> {

    List<CompanionPost> findByCreatorOrderByCreatedAtDesc(User creator);

    List<CompanionPost> findByDestinationContainingAndStartDateGreaterThanEqualAndEndDateLessThanEqual(
            String destination, LocalDate startDate, LocalDate endDate
    );

    /** 公开结伴帖按创建时间倒序，取前若干条（用于推荐无结果时的兜底） */
    List<CompanionPost> findTop20ByVisibilityOrderByCreatedAtDesc(String visibility);

    List<CompanionPost> findByCreatorAndVisibilityOrderByCreatedAtDesc(User creator, String visibility);
}


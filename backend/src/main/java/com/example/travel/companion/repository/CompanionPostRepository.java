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
}


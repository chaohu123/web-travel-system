package com.example.travel.social.repository;

import com.example.travel.social.entity.TravelNote;
import com.example.travel.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelNoteRepository extends JpaRepository<TravelNote, Long> {

    List<TravelNote> findByAuthorOrderByCreatedAtDesc(User author);

    long countByAuthor(User author);

    /** 统计引用该路线的游记数量（用于路线「被使用」次数） */
    long countByRelatedPlanId(Long relatedPlanId);
}


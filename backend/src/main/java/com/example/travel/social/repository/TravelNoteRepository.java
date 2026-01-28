package com.example.travel.social.repository;

import com.example.travel.social.entity.TravelNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelNoteRepository extends JpaRepository<TravelNote, Long> {
}


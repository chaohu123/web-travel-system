package com.example.travel.companion.repository;

import com.example.travel.companion.entity.CompanionPost;
import com.example.travel.companion.entity.CompanionTeam;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanionTeamRepository extends JpaRepository<CompanionTeam, Long> {

    Optional<CompanionTeam> findFirstByPostOrderByIdAsc(CompanionPost post);
}


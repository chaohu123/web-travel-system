package com.example.travel.companion.repository;

import com.example.travel.companion.entity.CompanionTeam;
import com.example.travel.companion.entity.TeamShare;
import com.example.travel.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamShareRepository extends JpaRepository<TeamShare, Long> {

    boolean existsByTeamAndToUser(CompanionTeam team, User toUser);
}

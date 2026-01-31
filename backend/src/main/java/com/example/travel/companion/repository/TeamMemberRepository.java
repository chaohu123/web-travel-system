package com.example.travel.companion.repository;

import com.example.travel.companion.entity.CompanionTeam;
import com.example.travel.companion.entity.TeamMember;
import com.example.travel.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeam(CompanionTeam team);

    Optional<TeamMember> findByTeamAndUser(CompanionTeam team, User user);

    /** 当前用户加入的所有小队成员记录（用于消息中心「小队消息」） */
    List<TeamMember> findByUser(User user);
}


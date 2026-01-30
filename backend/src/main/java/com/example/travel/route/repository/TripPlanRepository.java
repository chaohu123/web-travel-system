package com.example.travel.route.repository;

import com.example.travel.route.entity.TripPlan;
import com.example.travel.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripPlanRepository extends JpaRepository<TripPlan, Long> {

    List<TripPlan> findByOwnerOrderByCreatedAtDesc(User owner);

    long countByOwner(User owner);

    /** 热门线路兜底：取最新一批用于计算热度 */
    List<TripPlan> findTop50ByOrderByCreatedAtDesc();
}


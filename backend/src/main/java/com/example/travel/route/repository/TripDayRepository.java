package com.example.travel.route.repository;

import com.example.travel.route.entity.TripDay;
import com.example.travel.route.entity.TripPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripDayRepository extends JpaRepository<TripDay, Long> {

    List<TripDay> findByPlanOrderByDayIndexAsc(TripPlan plan);
}


package com.example.travel.route.repository;

import com.example.travel.route.entity.TripActivity;
import com.example.travel.route.entity.TripDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripActivityRepository extends JpaRepository<TripActivity, Long> {

    List<TripActivity> findByTripDayOrderByStartTimeAscIdAsc(TripDay tripDay);
}


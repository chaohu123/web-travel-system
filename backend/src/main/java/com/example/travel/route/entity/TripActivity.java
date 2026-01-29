package com.example.travel.route.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_trip_activity")
public class TripActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id")
    private TripDay tripDay;

    private String type; // sight / food / hotel / other

    private String name;
    private String location; // 简单文字描述

    private String startTime; // 先用字符串，后续可换 LocalTime
    private String endTime;

    private String transport; // 步行 / 地铁 / 出租车 等
    private Integer estimatedCost;

    // 新增：经纬度，用于前端地图折线展示真实位置（可为空）
    private Double lng; // 经度
    private Double lat; // 纬度
}


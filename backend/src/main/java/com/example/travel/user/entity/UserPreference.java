package com.example.travel.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_user_preference")
public class UserPreference {

    @Id
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    private String travelStyle; // 旅行风格：背包客 / 休闲度假 等
    private Integer budgetMin;
    private Integer budgetMax;
    private String trafficPreference; // 交通偏好

    @Column(length = 512)
    private String tags; // 文本标签列表，后续可拆表

    @Column(length = 1024)
    private String interestWeightsJson; // 自然/文化/美食/购物/休闲等权重JSON
}


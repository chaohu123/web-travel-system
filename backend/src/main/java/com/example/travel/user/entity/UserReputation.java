package com.example.travel.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_user_reputation")
public class UserReputation {

    @Id
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    private Integer score; // 信誉积分
    private Integer level; // 等级
    private Integer totalTrips; // 完成行程次数
    private Integer positiveCount; // 好评次数
}


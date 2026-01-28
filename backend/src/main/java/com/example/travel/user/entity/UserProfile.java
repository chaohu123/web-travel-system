package com.example.travel.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_user_profile")
public class UserProfile {

    @Id
    private Long id; // 与 User 共用主键

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    private String nickname;
    private String avatar;
    private String gender; // male / female / other
    private Integer age;
    private String city;

    @Column(length = 512)
    private String intro; // 个人简介

    @Column(length = 256)
    private String slogan; // 旅行宣言
}


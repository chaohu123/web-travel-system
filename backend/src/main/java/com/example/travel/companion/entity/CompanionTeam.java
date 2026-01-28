package com.example.travel.companion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_companion_team")
public class CompanionTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "post_id")
    private CompanionPost post;

    private String name;

    private Long finalPlanId;

    @Column(length = 16)
    private String status; // forming / confirmed / finished
}


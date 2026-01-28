package com.example.travel.companion.entity;

import com.example.travel.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_companion_post")
public class CompanionPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    private Long relatedPlanId;

    private String destination;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer minPeople;

    private Integer maxPeople;

    private Integer budgetMin;

    private Integer budgetMax;

    @Column(length = 512)
    private String expectedMateDesc;

    @Column(length = 16)
    private String visibility; // public / friends / private

    @Column(length = 16)
    private String status; // open / locked / closed

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "open";
        }
        if (visibility == null) {
            visibility = "public";
        }
    }
}


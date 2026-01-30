package com.example.travel.companion.entity;

import com.example.travel.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_team_share", uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "to_user_id"}))
public class TeamShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private CompanionTeam team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id")
    private User toUser;

    private LocalDateTime sharedAt;

    @PrePersist
    public void prePersist() {
        sharedAt = LocalDateTime.now();
    }
}

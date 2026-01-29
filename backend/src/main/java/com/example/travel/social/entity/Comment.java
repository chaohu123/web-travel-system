package com.example.travel.social.entity;

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
@Table(name = "t_comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 32, nullable = false)
    private String targetType; // note / route / companion_team 等

    @Column(nullable = false)
    private Long targetId;

    @Column(length = 512, nullable = false)
    private String content;

    private Integer score; // 可选评分 1-5

    @Column(length = 256)
    private String tags; // 评价标签(逗号分隔，如 守时,好沟通,靠谱)

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}


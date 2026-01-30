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
@Table(name = "t_interaction_message")
public class InteractionMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 消息接收者
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    /**
     * 发起互动的用户
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    /**
     * 消息类型：LIKE / COMMENT
     */
    @Column(length = 16, nullable = false)
    private String type;

    /**
     * 目标类型：note / route
     */
    @Column(length = 32, nullable = false)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(length = 255)
    private String targetTitle;

    @Column(length = 512)
    private String contentPreview;

    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (read == null) {
            read = false;
        }
    }
}


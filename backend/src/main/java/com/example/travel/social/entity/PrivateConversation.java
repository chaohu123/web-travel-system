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
@Table(name = "t_private_conversation")
public class PrivateConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户1：约定为较小的用户ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    /**
     * 用户2：约定为较大的用户ID
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @Column(length = 255)
    private String lastMessagePreview;

    private LocalDateTime lastMessageTime;

    private Integer user1UnreadCount = 0;

    private Integer user2UnreadCount = 0;

    private Boolean user1Pinned = false;

    private Boolean user2Pinned = false;

    /** 用户1 删除会话时间（软删除，仅对该用户隐藏） */
    private LocalDateTime user1DeletedAt;

    /** 用户2 删除会话时间（软删除，仅对该用户隐藏） */
    private LocalDateTime user2DeletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (user1UnreadCount == null) user1UnreadCount = 0;
        if (user2UnreadCount == null) user2UnreadCount = 0;
        if (user1Pinned == null) user1Pinned = false;
        if (user2Pinned == null) user2Pinned = false;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}


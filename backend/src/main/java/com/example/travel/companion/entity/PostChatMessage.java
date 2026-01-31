package com.example.travel.companion.entity;

import com.example.travel.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 结伴帖内置沟通消息：每条记录对应该帖子下的一条站内聊天消息。
 * 用于结伴详情页「内置沟通」Tab 的持久化与多端同步。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "t_post_chat_message", indexes = {
    @Index(name = "idx_post_chat_post_created", columnList = "post_id,created_at")
})
public class PostChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private CompanionPost post;

    /** 发送者；为 null 表示系统消息（如小队成立欢迎语） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Lob
    @Column(nullable = false)
    private String content;

    /** 消息类型：text=文本，spot=景点，image=图片，route=路线，companion=结伴 */
    @Column(length = 16)
    private String type;

    /** 景点卡片时的 JSON 数据（name, location, routeId 等） */
    @Column(length = 1000)
    private String spotJson;

    /** 路线卡片时的 JSON 数据（routeId, title, destination, days 等） */
    @Column(length = 500)
    private String routeJson;

    /** 结伴卡片时的 JSON 数据（postId, destination, startDate, endDate 等） */
    @Column(length = 500)
    private String companionJson;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (type == null) {
            type = "text";
        }
    }
}

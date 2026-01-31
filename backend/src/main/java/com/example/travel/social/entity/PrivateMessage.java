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
@Table(name = "t_private_message")
public class PrivateMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private PrivateConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT NOT NULL")
    private String content;

    @Column(length = 16)
    private String type; // text/image/route/companion/spot

    @Lob
    @Column(name = "spot_json")
    private String spotJson; // type=spot 时景点卡片数据 JSON

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (type == null) {
            type = "text";
        }
    }
}


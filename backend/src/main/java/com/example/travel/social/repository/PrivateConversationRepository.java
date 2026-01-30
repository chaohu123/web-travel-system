package com.example.travel.social.repository;

import com.example.travel.social.entity.PrivateConversation;
import com.example.travel.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrivateConversationRepository extends JpaRepository<PrivateConversation, Long> {

    /**
     * 查找包含当前用户的会话，按最后消息时间倒序
     */
    Page<PrivateConversation> findByUser1OrUser2OrderByLastMessageTimeDesc(User user1, User user2, Pageable pageable);

    /**
     * 查找包含当前用户的所有会话（用于统计未读数）
     */
    List<PrivateConversation> findByUser1OrUser2(User user1, User user2);

    /**
     * 根据两个用户 ID 查找会话（约定 user1 为较小 ID，user2 为较大 ID）
     */
    java.util.Optional<PrivateConversation> findByUser1_IdAndUser2_Id(Long user1Id, Long user2Id);
}


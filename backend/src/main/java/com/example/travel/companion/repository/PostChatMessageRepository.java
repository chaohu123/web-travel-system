package com.example.travel.companion.repository;

import com.example.travel.companion.entity.CompanionPost;
import com.example.travel.companion.entity.PostChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 结伴帖内置聊天消息的持久化接口。
 */
public interface PostChatMessageRepository extends JpaRepository<PostChatMessage, Long> {

    /**
     * 按帖子 ID 查询该帖下的所有聊天消息，按创建时间升序（先发的在前）。
     */
    List<PostChatMessage> findByPostOrderByCreatedAtAsc(CompanionPost post);

    /**
     * 该帖最新一条聊天消息（用于小队消息列表的「最后一条」预览）。
     */
    Optional<PostChatMessage> findFirstByPostOrderByCreatedAtDesc(CompanionPost post);
}

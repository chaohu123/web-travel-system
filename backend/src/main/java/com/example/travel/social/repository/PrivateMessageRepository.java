package com.example.travel.social.repository;

import com.example.travel.social.entity.PrivateMessage;
import com.example.travel.social.entity.PrivateConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {

    List<PrivateMessage> findByConversationOrderByCreatedAtAsc(PrivateConversation conversation);
}


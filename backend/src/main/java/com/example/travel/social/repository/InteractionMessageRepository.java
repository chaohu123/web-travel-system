package com.example.travel.social.repository;

import com.example.travel.social.entity.InteractionMessage;
import com.example.travel.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InteractionMessageRepository extends JpaRepository<InteractionMessage, Long> {

    long countByRecipientAndReadIsFalse(User recipient);

    List<InteractionMessage> findByRecipientAndReadIsFalse(User recipient);

    Page<InteractionMessage> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    Page<InteractionMessage> findByRecipientAndTypeInOrderByCreatedAtDesc(User recipient, List<String> types, Pageable pageable);

    Optional<InteractionMessage> findByIdAndRecipient(Long id, User recipient);
}


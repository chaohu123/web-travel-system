package com.example.travel.social.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.social.dto.MessageDtos;
import com.example.travel.social.entity.InteractionMessage;
import com.example.travel.social.entity.PrivateConversation;
import com.example.travel.social.repository.InteractionMessageRepository;
import com.example.travel.social.repository.PrivateConversationRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.repository.UserProfileRepository;
import com.example.travel.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final InteractionMessageRepository interactionMessageRepository;
    private final PrivateConversationRepository privateConversationRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public MessageService(InteractionMessageRepository interactionMessageRepository,
                          PrivateConversationRepository privateConversationRepository,
                          UserRepository userRepository,
                          UserProfileRepository userProfileRepository) {
        this.interactionMessageRepository = interactionMessageRepository;
        this.privateConversationRepository = privateConversationRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("请先登录");
        }
        String username = auth.getName();
        return (username.contains("@")
                ? userRepository.findByEmail(username)
                : userRepository.findByPhone(username))
                .orElseThrow(() -> BusinessException.unauthorized("用户未登录"));
    }

    /**
     * 顶部概览：总未读数
     */
    public MessageDtos.Overview overview() {
        User current = getCurrentUser();
        long unreadInteraction = interactionMessageRepository.countByRecipientAndReadIsFalse(current);
        long unreadPrivate = privateConversationRepository.findByUser1OrUser2(current, current).stream()
                .mapToLong(conv -> {
                    if (conv.getUser1() != null && conv.getUser1().getId().equals(current.getId())) {
                        return conv.getUser1UnreadCount() == null ? 0 : conv.getUser1UnreadCount();
                    } else if (conv.getUser2() != null && conv.getUser2().getId().equals(current.getId())) {
                        return conv.getUser2UnreadCount() == null ? 0 : conv.getUser2UnreadCount();
                    }
                    return 0;
                })
                .sum();
        MessageDtos.Overview dto = new MessageDtos.Overview();
        dto.setTotalUnread(unreadInteraction + unreadPrivate);
        return dto;
    }

    /**
     * 互动消息列表（点赞/评论）
     */
    public MessageDtos.PagedResult<MessageDtos.InteractionMessageItem> listInteractions(int page,
                                                                                        int pageSize,
                                                                                        String category) {
        if (page < 1) page = 1;
        if (pageSize <= 0) pageSize = 10;
        User current = getCurrentUser();
        Pageable pageable = PageRequest.of(page - 1, pageSize);

        Page<InteractionMessage> msgPage;
        if ("like".equalsIgnoreCase(category)) {
            msgPage = interactionMessageRepository.findByRecipientAndTypeInOrderByCreatedAtDesc(
                    current, List.of("LIKE"), pageable);
        } else if ("comment".equalsIgnoreCase(category)) {
            msgPage = interactionMessageRepository.findByRecipientAndTypeInOrderByCreatedAtDesc(
                    current, List.of("COMMENT"), pageable);
        } else {
            msgPage = interactionMessageRepository.findByRecipientOrderByCreatedAtDesc(current, pageable);
        }

        List<MessageDtos.InteractionMessageItem> items = msgPage.getContent().stream()
                .map(this::toInteractionItem)
                .collect(Collectors.toList());

        MessageDtos.PagedResult<MessageDtos.InteractionMessageItem> result = new MessageDtos.PagedResult<>();
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setTotal(msgPage.getTotalElements());
        result.setList(items);
        return result;
    }

    private MessageDtos.InteractionMessageItem toInteractionItem(InteractionMessage msg) {
        MessageDtos.InteractionMessageItem dto = new MessageDtos.InteractionMessageItem();
        dto.setId(msg.getId());
        dto.setType(msg.getType());
        if (msg.getFromUser() != null) {
            dto.setFromUserId(msg.getFromUser().getId());
            // 昵称优先取 profile.nickname，其次 email/phone
            User from = msg.getFromUser();
            UserProfile profile = userProfileRepository.findById(from.getId()).orElse(null);
            if (profile != null && profile.getNickname() != null) {
                dto.setFromUserName(profile.getNickname());
                dto.setFromUserAvatar(profile.getAvatar());
            } else {
                String name = from.getEmail() != null ? from.getEmail() : from.getPhone();
                dto.setFromUserName(name);
            }
        }
        dto.setTargetType(msg.getTargetType());
        dto.setTargetId(msg.getTargetId());
        dto.setTargetTitle(msg.getTargetTitle());
        dto.setContentPreview(msg.getContentPreview());
        dto.setCreatedAt(msg.getCreatedAt());
        dto.setRead(msg.getRead());
        return dto;
    }

    /**
     * 私信会话列表
     */
    public MessageDtos.PagedResult<MessageDtos.ConversationSummary> listConversations(int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize <= 0) pageSize = 10;
        User current = getCurrentUser();
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<PrivateConversation> convPage =
                privateConversationRepository.findByUser1OrUser2OrderByLastMessageTimeDesc(current, current, pageable);

        List<MessageDtos.ConversationSummary> list = convPage.getContent().stream()
                .map(conv -> toConversationSummary(conv, current))
                .collect(Collectors.toList());

        MessageDtos.PagedResult<MessageDtos.ConversationSummary> result = new MessageDtos.PagedResult<>();
        result.setPage(page);
        result.setPageSize(pageSize);
        result.setTotal(convPage.getTotalElements());
        result.setList(list);
        return result;
    }

    private MessageDtos.ConversationSummary toConversationSummary(PrivateConversation conv, User current) {
        MessageDtos.ConversationSummary dto = new MessageDtos.ConversationSummary();
        dto.setId(conv.getId());
        User peer;
        Integer unread;
        Boolean pinned;
        if (conv.getUser1() != null && conv.getUser1().getId().equals(current.getId())) {
            peer = conv.getUser2();
            unread = conv.getUser1UnreadCount();
            pinned = conv.getUser1Pinned();
        } else {
            peer = conv.getUser1();
            unread = conv.getUser2UnreadCount();
            pinned = conv.getUser2Pinned();
        }
        if (peer != null) {
            dto.setPeerUserId(peer.getId());
            UserProfile profile = userProfileRepository.findById(peer.getId()).orElse(null);
            if (profile != null && profile.getNickname() != null) {
                dto.setPeerNickname(profile.getNickname());
                dto.setPeerAvatar(profile.getAvatar());
            } else {
                String name = peer.getEmail() != null ? peer.getEmail() : peer.getPhone();
                dto.setPeerNickname(name);
            }
        }
        dto.setLastMessagePreview(conv.getLastMessagePreview());
        dto.setLastMessageTime(conv.getLastMessageTime());
        dto.setUnreadCount(unread == null ? 0 : unread);
        dto.setPinned(pinned != null && pinned);
        return dto;
    }

    /**
     * 标记单条互动消息为已读
     */
    @Transactional
    public void markInteractionRead(Long id) {
        User current = getCurrentUser();
        InteractionMessage msg = interactionMessageRepository.findByIdAndRecipient(id, current)
                .orElseThrow(() -> BusinessException.badRequest("消息不存在"));
        if (Boolean.FALSE.equals(msg.getRead())) {
            msg.setRead(true);
            interactionMessageRepository.save(msg);
        }
    }

    /**
     * 将所有互动消息标记为已读
     */
    @Transactional
    public void markAllInteractionRead() {
        User current = getCurrentUser();
        List<InteractionMessage> unreadList = interactionMessageRepository.findByRecipientAndReadIsFalse(current);
        if (unreadList.isEmpty()) return;
        unreadList.forEach(m -> m.setRead(true));
        interactionMessageRepository.saveAll(unreadList);
    }

    /**
     * 清空指定会话的未读数（当前用户视角）
     */
    @Transactional
    public void clearConversationUnread(Long conversationId) {
        User current = getCurrentUser();
        PrivateConversation conv = privateConversationRepository.findById(conversationId)
                .orElseThrow(() -> BusinessException.badRequest("会话不存在"));
        if (conv.getUser1() != null && conv.getUser1().getId().equals(current.getId())) {
            conv.setUser1UnreadCount(0);
        } else if (conv.getUser2() != null && conv.getUser2().getId().equals(current.getId())) {
            conv.setUser2UnreadCount(0);
        } else {
            throw BusinessException.badRequest("会话不属于当前用户");
        }
        privateConversationRepository.save(conv);
    }
}


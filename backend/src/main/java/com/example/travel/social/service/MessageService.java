package com.example.travel.social.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.social.dto.MessageDtos;
import com.example.travel.social.entity.InteractionMessage;
import com.example.travel.social.entity.PrivateConversation;
import com.example.travel.social.entity.PrivateMessage;
import com.example.travel.social.repository.InteractionMessageRepository;
import com.example.travel.social.repository.PrivateConversationRepository;
import com.example.travel.social.repository.PrivateMessageRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.repository.UserFollowRepository;
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
    private final PrivateMessageRepository privateMessageRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserFollowRepository userFollowRepository;

    public MessageService(InteractionMessageRepository interactionMessageRepository,
                          PrivateConversationRepository privateConversationRepository,
                          PrivateMessageRepository privateMessageRepository,
                          UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          UserFollowRepository userFollowRepository) {
        this.interactionMessageRepository = interactionMessageRepository;
        this.privateConversationRepository = privateConversationRepository;
        this.privateMessageRepository = privateMessageRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userFollowRepository = userFollowRepository;
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
            // 对方是否为当前用户的粉丝（对方关注了当前用户）
            dto.setPeerIsFollower(userFollowRepository.existsByFollowerAndFollowee(peer, current));
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

    /**
     * 发送私信给指定用户，若会话不存在则创建
     */
    @Transactional
    public MessageDtos.ChatMessageItem sendChatMessage(Long peerUserId, String content) {
        if (content == null || content.isBlank()) {
            throw BusinessException.badRequest("消息内容不能为空");
        }
        User current = getCurrentUser();
        if (current.getId().equals(peerUserId)) {
            throw BusinessException.badRequest("不能给自己发私信");
        }
        User peer = userRepository.findById(peerUserId)
                .orElseThrow(() -> BusinessException.badRequest("对方用户不存在"));

        Long u1Id = Math.min(current.getId(), peerUserId);
        Long u2Id = Math.max(current.getId(), peerUserId);
        PrivateConversation conv = privateConversationRepository
                .findByUser1_IdAndUser2_Id(u1Id, u2Id)
                .orElseGet(() -> {
                    PrivateConversation newConv = new PrivateConversation();
                    newConv.setUser1(userRepository.findById(u1Id).orElseThrow());
                    newConv.setUser2(userRepository.findById(u2Id).orElseThrow());
                    newConv.setUser1UnreadCount(0);
                    newConv.setUser2UnreadCount(0);
                    return privateConversationRepository.save(newConv);
                });

        PrivateMessage msg = new PrivateMessage();
        msg.setConversation(conv);
        msg.setSender(current);
        msg.setContent(content.trim());
        msg.setType("text");
        msg = privateMessageRepository.save(msg);

        String preview = content.length() > 50 ? content.substring(0, 50) + "…" : content;
        conv.setLastMessagePreview(preview);
        conv.setLastMessageTime(msg.getCreatedAt());
        // 给接收方（peer）增加未读数：由于 user1 是较小 ID，user2 是较大 ID
        // 如果 peerUserId == u1Id，则 peer 是 user1；如果 peerUserId == u2Id，则 peer 是 user2
        if (u1Id.equals(peerUserId)) {
            conv.setUser1UnreadCount((conv.getUser1UnreadCount() == null ? 0 : conv.getUser1UnreadCount()) + 1);
        } else if (u2Id.equals(peerUserId)) {
            conv.setUser2UnreadCount((conv.getUser2UnreadCount() == null ? 0 : conv.getUser2UnreadCount()) + 1);
        }
        privateConversationRepository.save(conv);

        MessageDtos.ChatMessageItem dto = new MessageDtos.ChatMessageItem();
        dto.setId(msg.getId());
        dto.setSenderId(current.getId());
        dto.setContent(msg.getContent());
        dto.setType(msg.getType());
        dto.setCreatedAt(msg.getCreatedAt());
        return dto;
    }

    /**
     * 获取与指定用户的私信消息列表（按时间正序）
     */
    public List<MessageDtos.ChatMessageItem> getChatMessagesWithPeer(Long peerUserId) {
        User current = getCurrentUser();
        if (current.getId().equals(peerUserId)) {
            throw BusinessException.badRequest("无法与自己会话");
        }
        Long u1Id = Math.min(current.getId(), peerUserId);
        Long u2Id = Math.max(current.getId(), peerUserId);
        PrivateConversation conv = privateConversationRepository
                .findByUser1_IdAndUser2_Id(u1Id, u2Id)
                .orElse(null);
        if (conv == null) {
            return List.of();
        }
        List<PrivateMessage> list = privateMessageRepository.findByConversationOrderByCreatedAtAsc(conv);
        return list.stream().map(m -> {
            MessageDtos.ChatMessageItem dto = new MessageDtos.ChatMessageItem();
            dto.setId(m.getId());
            dto.setSenderId(m.getSender() != null ? m.getSender().getId() : null);
            dto.setContent(m.getContent());
            dto.setType(m.getType() != null ? m.getType() : "text");
            dto.setCreatedAt(m.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 按对方用户清空当前用户在该会话的未读数（进入聊天页时调用）
     */
    @Transactional
    public void clearConversationUnreadByPeer(Long peerUserId) {
        User current = getCurrentUser();
        Long u1Id = Math.min(current.getId(), peerUserId);
        Long u2Id = Math.max(current.getId(), peerUserId);
        privateConversationRepository.findByUser1_IdAndUser2_Id(u1Id, u2Id).ifPresent(conv -> {
            if (conv.getUser1() != null && conv.getUser1().getId().equals(current.getId())) {
                conv.setUser1UnreadCount(0);
            } else {
                conv.setUser2UnreadCount(0);
            }
            privateConversationRepository.save(conv);
        });
    }
}


package com.example.travel.social.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.route.dto.TripPlanDtos;
import com.example.travel.route.service.RoutePlanService;
import com.example.travel.social.dto.TravelNoteDtos;
import com.example.travel.social.entity.TravelNote;
import com.example.travel.social.repository.CommentRepository;
import com.example.travel.social.repository.ContentLikeRepository;
import com.example.travel.social.repository.TravelNoteRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.repository.UserProfileRepository;
import com.example.travel.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TravelNoteService {

    private final TravelNoteRepository travelNoteRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ContentLikeRepository contentLikeRepository;
    private final CommentRepository commentRepository;
    private final RoutePlanService routePlanService;

    public TravelNoteService(TravelNoteRepository travelNoteRepository,
                             UserRepository userRepository,
                             UserProfileRepository userProfileRepository,
                             ContentLikeRepository contentLikeRepository,
                             CommentRepository commentRepository,
                             RoutePlanService routePlanService) {
        this.travelNoteRepository = travelNoteRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.contentLikeRepository = contentLikeRepository;
        this.commentRepository = commentRepository;
        this.routePlanService = routePlanService;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return (username.contains("@")
                ? userRepository.findByEmail(username)
                : userRepository.findByPhone(username))
                .orElseThrow(() -> BusinessException.unauthorized("用户未登录"));
    }

    @Transactional
    public Long create(TravelNoteDtos.CreateRequest req) {
        User author = getCurrentUser();
        TravelNote note = new TravelNote();
        note.setAuthor(author);
        note.setTitle(req.getTitle());
        note.setContent(req.getContent());
        note.setCoverImage(req.getCoverImage());
        note.setRelatedPlanId(req.getRelatedPlanId());
        note.setDestination(req.getDestination());
        travelNoteRepository.save(note);
        return note.getId();
    }

    public List<TravelNoteDtos.Summary> list() {
        List<TravelNote> list = travelNoteRepository.findAll();
        return list.stream().map(this::toSummary).collect(Collectors.toList());
    }

    public TravelNoteDtos.Detail getOne(Long id) {
        TravelNote note = travelNoteRepository.findById(id)
                .orElseThrow(() -> BusinessException.badRequest("游记不存在"));
        return toDetail(note);
    }

    /**
     * 根据游记关联的路线自动生成「相关景点推荐」列表（来自路线中的景点/活动）。
     * 未关联路线或路线无活动时返回空列表。
     */
    public List<TravelNoteDtos.RelatedSpotItem> getRelatedSpots(Long noteId) {
        TravelNote note = travelNoteRepository.findById(noteId)
                .orElseThrow(() -> BusinessException.badRequest("游记不存在"));
        Long planId = note.getRelatedPlanId();
        if (planId == null) {
            return List.of();
        }
        TripPlanDtos.PlanResponse plan;
        try {
            plan = routePlanService.getPlan(planId);
        } catch (Exception e) {
            return List.of();
        }
        if (plan.getDays() == null || plan.getDays().isEmpty()) {
            return List.of();
        }
        List<TravelNoteDtos.RelatedSpotItem> list = new ArrayList<>();
        int idx = 0;
        for (TripPlanDtos.Day day : plan.getDays()) {
            if (day.getActivities() == null) continue;
            for (TripPlanDtos.Activity act : day.getActivities()) {
                String name = (act.getName() != null ? act.getName().trim() : null);
                if (name == null || name.isEmpty()) {
                    name = (act.getLocation() != null ? act.getLocation().trim() : null);
                }
                if (name == null || name.isEmpty()) continue;
                TravelNoteDtos.RelatedSpotItem item = new TravelNoteDtos.RelatedSpotItem();
                item.setId("route-" + planId + "-" + idx);
                item.setName(name);
                if (act.getLocation() != null && !act.getLocation().trim().equals(name)) {
                    item.setLocation(act.getLocation().trim());
                }
                item.setType(act.getType());
                item.setTimeRange(buildTimeRange(act.getStartTime(), act.getEndTime()));
                item.setImageUrl(null);
                item.setRouteId(planId);
                list.add(item);
                idx++;
            }
        }
        return list.stream().limit(6).collect(Collectors.toList());
    }

    private static String buildTimeRange(String start, String end) {
        if (start == null && end == null) return null;
        if (start != null && end != null) return start + "–" + end;
        return start != null ? start : end;
    }

    @Transactional
    public void update(Long id, TravelNoteDtos.UpdateRequest req) {
        User current = getCurrentUser();
        TravelNote note = travelNoteRepository.findById(id)
                .orElseThrow(() -> BusinessException.badRequest("游记不存在"));
        if (note.getAuthor() == null || !note.getAuthor().getId().equals(current.getId())) {
            throw BusinessException.forbidden("只能编辑自己的游记");
        }
        note.setTitle(req.getTitle());
        note.setContent(req.getContent());
        note.setCoverImage(req.getCoverImage());
        note.setRelatedPlanId(req.getRelatedPlanId());
        note.setDestination(req.getDestination());
        travelNoteRepository.save(note);
    }

    @Transactional
    public void delete(Long id) {
        User current = getCurrentUser();
        TravelNote note = travelNoteRepository.findById(id)
                .orElseThrow(() -> BusinessException.badRequest("游记不存在"));
        if (note.getAuthor() == null || !note.getAuthor().getId().equals(current.getId())) {
            throw BusinessException.forbidden("只能删除自己的游记");
        }
        travelNoteRepository.delete(note);
    }

    private TravelNoteDtos.Summary toSummary(TravelNote note) {
        TravelNoteDtos.Summary dto = new TravelNoteDtos.Summary();
        dto.setId(note.getId());
        dto.setTitle(note.getTitle());
        dto.setDestination(note.getDestination());
        dto.setCoverImage(note.getCoverImage());
        if (note.getAuthor() != null) {
            dto.setAuthorId(note.getAuthor().getId());
            dto.setAuthorName(resolveAuthorName(note.getAuthor()));
        }
        dto.setCreatedAt(note.getCreatedAt());
        dto.setLikeCount(contentLikeRepository.countByTargetTypeAndTargetId("note", note.getId()));
        dto.setCommentCount(commentRepository.countByTargetTypeAndTargetId("note", note.getId()));
        return dto;
    }

    private TravelNoteDtos.Detail toDetail(TravelNote note) {
        TravelNoteDtos.Detail dto = new TravelNoteDtos.Detail();
        dto.setId(note.getId());
        dto.setTitle(note.getTitle());
        dto.setContent(note.getContent());
        dto.setCoverImage(note.getCoverImage());
        dto.setRelatedPlanId(note.getRelatedPlanId());
        dto.setDestination(note.getDestination());
        if (note.getAuthor() != null) {
            dto.setAuthorId(note.getAuthor().getId());
            dto.setAuthorName(resolveAuthorName(note.getAuthor()));
        }
        dto.setCreatedAt(note.getCreatedAt());
        return dto;
    }

    /** 优先使用昵称，无昵称时回退到邮箱/手机号 */
    private String resolveAuthorName(User user) {
        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        if (profile != null && profile.getNickname() != null && !profile.getNickname().isBlank()) {
            return profile.getNickname();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return user.getPhone() != null ? user.getPhone() : "用户";
    }
}


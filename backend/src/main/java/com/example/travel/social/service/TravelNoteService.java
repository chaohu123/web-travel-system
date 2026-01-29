package com.example.travel.social.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.social.dto.TravelNoteDtos;
import com.example.travel.social.entity.TravelNote;
import com.example.travel.social.repository.TravelNoteRepository;
import com.example.travel.user.entity.User;
import com.example.travel.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TravelNoteService {

    private final TravelNoteRepository travelNoteRepository;
    private final UserRepository userRepository;

    public TravelNoteService(TravelNoteRepository travelNoteRepository,
                             UserRepository userRepository) {
        this.travelNoteRepository = travelNoteRepository;
        this.userRepository = userRepository;
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

    private TravelNoteDtos.Summary toSummary(TravelNote note) {
        TravelNoteDtos.Summary dto = new TravelNoteDtos.Summary();
        dto.setId(note.getId());
        dto.setTitle(note.getTitle());
        dto.setDestination(note.getDestination());
        dto.setCoverImage(note.getCoverImage());
        if (note.getAuthor() != null) {
            dto.setAuthorId(note.getAuthor().getId());
            dto.setAuthorName(note.getAuthor().getEmail());
        }
        dto.setCreatedAt(note.getCreatedAt());
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
            dto.setAuthorName(note.getAuthor().getEmail());
        }
        dto.setCreatedAt(note.getCreatedAt());
        return dto;
    }
}


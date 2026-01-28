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

    private TravelNoteDtos.Summary toSummary(TravelNote note) {
        TravelNoteDtos.Summary dto = new TravelNoteDtos.Summary();
        dto.setId(note.getId());
        dto.setTitle(note.getTitle());
        dto.setDestination(note.getDestination());
        dto.setCoverImage(note.getCoverImage());
        dto.setAuthorName(note.getAuthor() != null ? note.getAuthor().getEmail() : "");
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
        dto.setAuthorName(note.getAuthor() != null ? note.getAuthor().getEmail() : "");
        dto.setCreatedAt(note.getCreatedAt());
        return dto;
    }
}


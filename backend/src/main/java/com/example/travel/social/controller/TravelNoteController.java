package com.example.travel.social.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.social.dto.TravelNoteDtos;
import com.example.travel.social.service.TravelNoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class TravelNoteController {

    private final TravelNoteService travelNoteService;

    public TravelNoteController(TravelNoteService travelNoteService) {
        this.travelNoteService = travelNoteService;
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody TravelNoteDtos.CreateRequest request) {
        return ApiResponse.success(travelNoteService.create(request));
    }

    @GetMapping
    public ApiResponse<List<TravelNoteDtos.Summary>> list() {
        return ApiResponse.success(travelNoteService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<TravelNoteDtos.Detail> getOne(@PathVariable Long id) {
        return ApiResponse.success(travelNoteService.getOne(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody TravelNoteDtos.UpdateRequest request) {
        travelNoteService.update(id, request);
        return ApiResponse.success();
    }
}


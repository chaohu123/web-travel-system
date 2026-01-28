package com.example.travel.user.controller;

import com.example.travel.common.api.ApiResponse;
import com.example.travel.user.dto.UserDtos;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.entity.UserReputation;
import com.example.travel.user.repository.UserProfileRepository;
import com.example.travel.user.repository.UserRepository;
import com.example.travel.user.repository.UserReputationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserReputationRepository userReputationRepository;

    public UserController(UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          UserReputationRepository userReputationRepository) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userReputationRepository = userReputationRepository;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = username.contains("@")
                ? userRepository.findByEmail(username).orElse(null)
                : userRepository.findByPhone(username).orElse(null);

        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        Map<String, Object> data = Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "phone", user.getPhone()
        );
        return ApiResponse.success(data);
    }

    @GetMapping("/me/detail")
    public ApiResponse<UserDtos.MeDetail> meDetail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = username.contains("@")
                ? userRepository.findByEmail(username).orElse(null)
                : userRepository.findByPhone(username).orElse(null);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);
        UserReputation reputation = userReputationRepository.findById(user.getId()).orElse(null);

        UserDtos.MeDetail dto = new UserDtos.MeDetail();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        if (profile != null) {
            dto.setNickname(profile.getNickname());
            dto.setAvatar(profile.getAvatar());
            dto.setCity(profile.getCity());
            dto.setGender(profile.getGender());
            dto.setAge(profile.getAge());
            dto.setIntro(profile.getIntro());
            dto.setSlogan(profile.getSlogan());
        }
        if (reputation != null) {
            dto.setReputationScore(reputation.getScore());
            dto.setReputationLevel(reputation.getLevel());
        }

        return ApiResponse.success(dto);
    }

    @PutMapping("/me/profile")
    public ApiResponse<UserDtos.MeDetail> updateProfile(@Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        User user = username.contains("@")
                ? userRepository.findByEmail(username).orElse(null)
                : userRepository.findByPhone(username).orElse(null);
        if (user == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(new UserProfile());
        if (profile.getUser() == null) {
            profile.setUser(user);
            profile.setId(user.getId());
        }
        if (request.getNickname() != null) profile.setNickname(request.getNickname());
        if (request.getAvatar() != null) profile.setAvatar(request.getAvatar());
        if (request.getGender() != null) profile.setGender(request.getGender());
        if (request.getAge() != null) profile.setAge(request.getAge());
        if (request.getCity() != null) profile.setCity(request.getCity());
        if (request.getIntro() != null) profile.setIntro(request.getIntro());
        if (request.getSlogan() != null) profile.setSlogan(request.getSlogan());
        userProfileRepository.save(profile);
        return meDetail();
    }
}


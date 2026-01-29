package com.example.travel.user.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.security.JwtUtil;
import com.example.travel.user.dto.AuthDtos;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserPreference;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.entity.UserReputation;
import com.example.travel.user.repository.UserRepository;
import com.example.travel.user.repository.UserProfileRepository;
import com.example.travel.user.repository.UserPreferenceRepository;
import com.example.travel.user.repository.UserReputationRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserReputationRepository userReputationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       UserProfileRepository userProfileRepository,
                       UserPreferenceRepository userPreferenceRepository,
                       UserReputationRepository userReputationRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.userReputationRepository = userReputationRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public void register(AuthDtos.RegisterRequest request) {
        if (!StringUtils.hasText(request.getEmail()) && !StringUtils.hasText(request.getPhone())) {
            throw BusinessException.badRequest("邮箱和手机号至少填一个");
        }

        userRepository.findByEmail(request.getEmail()).ifPresent(u -> {
            throw BusinessException.badRequest("邮箱已被注册");
        });
        if (StringUtils.hasText(request.getPhone())) {
            userRepository.findByPhone(request.getPhone()).ifPresent(u -> {
                throw BusinessException.badRequest("手机号已被注册");
            });
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        // 使用 BCrypt 加密密码
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        user.setPassword(encodedPassword);
        user.setAuthProvider("local");

        user = userRepository.saveAndFlush(user); // 使用 saveAndFlush 确保 id 立即生成

        // 保存关联资料（使用 @MapsId，只需设置 user，id 会自动从 user.getId() 获取）
        UserProfile profile = new UserProfile();
        profile.setUser(user); // @MapsId 会自动设置 id = user.getId()
        profile.setNickname("旅人" + user.getId());
        userProfileRepository.save(profile);

        // 保存偏好设置
        UserPreference preference = new UserPreference();
        preference.setUser(user); // @MapsId 会自动设置 id = user.getId()
        userPreferenceRepository.save(preference);

        // 保存信誉信息
        UserReputation reputation = new UserReputation();
        reputation.setUser(user); // @MapsId 会自动设置 id = user.getId()
        reputation.setScore(0);
        reputation.setLevel(1);
        reputation.setTotalTrips(0);
        reputation.setPositiveCount(0);
        userReputationRepository.save(reputation);
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        if (!authentication.isAuthenticated()) {
            throw BusinessException.unauthorized("账号或密码错误");
        }

        User user;
        if (request.getUsername().contains("@")) {
            user = userRepository.findByEmail(request.getUsername())
                    .orElseThrow(() -> BusinessException.unauthorized("账号或密码错误"));
        } else {
            user = userRepository.findByPhone(request.getUsername())
                    .orElseThrow(() -> BusinessException.unauthorized("账号或密码错误"));
        }

        String token = JwtUtil.generateToken(request.getUsername(), user.getId());
        AuthDtos.LoginResponse resp = new AuthDtos.LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setUsername(request.getUsername());
        return resp;
    }
}


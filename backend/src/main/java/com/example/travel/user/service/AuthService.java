package com.example.travel.user.service;

import com.example.travel.common.exception.BusinessException;
import com.example.travel.security.JwtUtil;
import com.example.travel.user.dto.AuthDtos;
import com.example.travel.user.entity.User;
import com.example.travel.user.entity.UserPreference;
import com.example.travel.user.entity.UserProfile;
import com.example.travel.user.entity.UserReputation;
import com.example.travel.user.repository.UserRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
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
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setAuthProvider("local");

        userRepository.save(user);

        // 关联资料 / 偏好 / 信誉，先用空数据占位，后续可补充
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setNickname("旅人" + user.getId());

        UserPreference preference = new UserPreference();
        preference.setUser(user);

        UserReputation reputation = new UserReputation();
        reputation.setUser(user);
        reputation.setScore(0);
        reputation.setLevel(1);
        reputation.setTotalTrips(0);
        reputation.setPositiveCount(0);

        // 这里为了简单先不单独建 repository，后续根据需要拆分
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


package com.example.travel.user.service;

import com.example.travel.user.entity.User;
import com.example.travel.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 密码迁移服务：用于将数据库中的明文密码迁移为 BCrypt 哈希密码
 * 注意：此服务仅用于一次性数据迁移，迁移完成后可删除或禁用
 */
@Service
public class PasswordMigrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordMigrationService(UserRepository userRepository,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 检查密码是否为 BCrypt 格式（以 $2a$, $2b$, $2y$ 开头）
     */
    private boolean isBcryptHash(String password) {
        if (password == null || password.length() < 10) {
            return false;
        }
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }

    /**
     * 迁移所有明文密码为 BCrypt 哈希
     * 警告：此方法会修改数据库，请谨慎使用
     * 
     * @param dryRun 如果为 true，只检查不实际修改
     * @return 迁移的用户数量
     */
    @Transactional
    public int migratePlainTextPasswords(boolean dryRun) {
        List<User> allUsers = userRepository.findAll();
        int migratedCount = 0;

        for (User user : allUsers) {
            String currentPassword = user.getPassword();
            if (currentPassword != null && !isBcryptHash(currentPassword)) {
                // 检测到明文密码
                if (!dryRun) {
                    // 注意：这里假设明文密码就是原始密码
                    // 如果数据库中存储的是其他格式，需要相应调整
                    String hashedPassword = passwordEncoder.encode(currentPassword);
                    user.setPassword(hashedPassword);
                    userRepository.save(user);
                }
                migratedCount++;
            }
        }

        return migratedCount;
    }

    /**
     * 为指定用户重新加密密码（用于密码重置等场景）
     */
    @Transactional
    public void rehashPassword(Long userId, String plainPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setPassword(passwordEncoder.encode(plainPassword));
        userRepository.save(user);
    }
}

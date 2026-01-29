# 密码哈希匹配问题排查指南

## 问题描述
登录时出现 `BadCredentialsException: 用户名或密码错误`，可能是密码哈希不匹配导致的。

## 可能的原因

### 1. 数据库中存储的是明文密码
如果数据库中有旧数据，密码可能是明文存储的，而登录时使用 BCrypt 验证会失败。

**解决方案：**
- 使用 `PasswordMigrationService.migratePlainTextPasswords(false)` 迁移所有明文密码
- 或者让用户重新注册/重置密码

### 2. 密码编码器配置不一致
确保注册和登录使用相同的 `PasswordEncoder`。

**检查点：**
- `SecurityConfig.passwordEncoder()` 返回 `BCryptPasswordEncoder`
- `AuthService.register()` 使用 `passwordEncoder.encode()`
- `AuthenticationManager` 自动使用相同的 `PasswordEncoder`

### 3. 用户不存在或账号格式错误
确保 `CustomUserDetailsService.loadUserByUsername()` 能正确找到用户。

## 验证步骤

### 1. 检查数据库中的密码格式
BCrypt 哈希密码应该以 `$2a$`、`$2b$` 或 `$2y$` 开头，长度约 60 字符。

```sql
SELECT id, email, phone, 
       LEFT(password, 10) as password_prefix,
       LENGTH(password) as password_length
FROM t_user 
LIMIT 10;
```

### 2. 测试密码加密
在代码中测试：

```java
@Autowired
private PasswordEncoder passwordEncoder;

public void testPassword() {
    String plain = "test123456";
    String encoded = passwordEncoder.encode(plain);
    boolean matches = passwordEncoder.matches(plain, encoded);
    System.out.println("Encoded: " + encoded);
    System.out.println("Matches: " + matches); // 应该为 true
}
```

### 3. 检查注册流程
确保注册时密码被正确加密：

```java
// 在 AuthService.register() 中
String encodedPassword = passwordEncoder.encode(request.getPassword());
user.setPassword(encodedPassword);
```

### 4. 检查登录流程
确保 `AuthenticationManager` 使用正确的 `UserDetailsService` 和 `PasswordEncoder`：

- `SecurityConfig` 中配置了 `PasswordEncoder` Bean
- `SecurityConfig` 中配置了 `UserDetailsService` Bean
- `AuthenticationManager` 通过 `AuthenticationConfiguration` 自动配置

## 解决方案

### 方案 1：重新注册用户（推荐用于测试）
删除旧用户数据，让用户重新注册，新注册的用户密码会被正确加密。

### 方案 2：密码迁移工具
使用 `PasswordMigrationService` 迁移现有明文密码：

```java
@Autowired
private PasswordMigrationService migrationService;

// 先检查有多少需要迁移
int count = migrationService.migratePlainTextPasswords(true);
System.out.println("需要迁移的用户数: " + count);

// 执行迁移（注意：这会修改数据库）
if (count > 0) {
    migrationService.migratePlainTextPasswords(false);
}
```

**注意：** 此方法假设数据库中的明文密码就是原始密码。如果数据库中的密码已经是其他格式（如 MD5），需要相应调整迁移逻辑。

### 方案 3：重置密码功能
实现密码重置功能，让用户通过邮箱/手机号重置密码，新密码会被正确加密。

## 调试技巧

### 1. 启用 Spring Security 调试日志
在 `application.yml` 中添加：

```yaml
logging:
  level:
    org.springframework.security: DEBUG
```

### 2. 在登录方法中添加日志
```java
public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
    try {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        // ...
    } catch (BadCredentialsException e) {
        // 记录详细信息
        User user = userRepository.findByEmail(request.getUsername())
                .orElse(null);
        if (user != null) {
            System.out.println("用户存在，密码前缀: " + user.getPassword().substring(0, Math.min(20, user.getPassword().length())));
        }
        throw e;
    }
}
```

### 3. 验证密码匹配
```java
// 手动验证密码
User user = userRepository.findByEmail("test@example.com").orElse(null);
if (user != null) {
    boolean matches = passwordEncoder.matches("原始密码", user.getPassword());
    System.out.println("密码匹配: " + matches);
}
```

## 常见错误

1. **密码长度不足**：BCrypt 要求密码至少有一定长度，确保密码符合要求
2. **特殊字符问题**：某些特殊字符在传输或存储时可能被转义，检查 URL 编码
3. **前后空格**：确保密码输入时没有多余的前后空格

## 预防措施

1. **始终使用 PasswordEncoder**：不要直接存储明文密码
2. **统一编码器**：确保整个应用使用同一个 `PasswordEncoder` 实例
3. **测试覆盖**：编写单元测试验证密码加密和验证流程

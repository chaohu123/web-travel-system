package com.example.travel.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthDtos {

    @Data
    public static class RegisterRequest {
        @Email(message = "邮箱格式不正确")
        private String email;

        private String phone;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 32, message = "密码长度需在6-32位之间")
        private String password;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "账号不能为空")
        private String username; // 可以是邮箱或手机号

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class LoginResponse {
        private String token;
        private Long userId;
        private String username;
    }
}


package com.example.travel.security;

import com.example.travel.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证访问受保护 API 时返回 401 + JSON（与业务 ApiResponse 格式一致），
 * 便于前端区分「未登录/登录过期」并跳转登录页。默认 Spring Security 会返回 403，导致前端无法正确跳转。
 */
@Component
public class Http401UnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiResponse<Void> body = ApiResponse.error(401, "未登录或登录已过期，请重新登录");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
    }
}

package com.czl.teamupbackend.commen.filter;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.enums.AuthErrorType;
import com.czl.teamupbackend.commen.jwt.JwtTokenUtil;
import com.czl.teamupbackend.commen.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtTokenUtil jwtTokenUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        UserContext.removeCurrentUser();
        String path = request.getRequestURI();
        try {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublicPath(path)) {
                filterChain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                writeUnauthorized(response, "未登录或Token缺失", AuthErrorType.UNAUTHORIZED);
                return;
            }

            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                writeUnauthorized(response, "未登录或Token缺失", AuthErrorType.UNAUTHORIZED);
                return;
            }

            AuthErrorType authError = jwtTokenUtil.validateToken(token);
            if (authError != null) {
                if (authError == AuthErrorType.TOKEN_EXPIRED) {
                    writeUnauthorized(response, "登录已过期，请重新登录", AuthErrorType.TOKEN_EXPIRED);
                } else {
                    writeUnauthorized(response, "Token无效，请重新登录", AuthErrorType.TOKEN_INVALID);
                }
                return;
            }

            filterChain.doFilter(request, response);
        } finally {
            UserContext.removeCurrentUser();
        }
    }

    private boolean isPublicPath(String path) {
        if (path == null) {
            return true;
        }
        return path.startsWith("/user/login")
            || path.startsWith("/user/register")
            || path.startsWith("/ws")
            || path.startsWith("/ws-notify")
            || path.startsWith("/error")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-ui")
            || path.startsWith("/swagger-resources")
            || path.startsWith("/webjars");
    }

    private void writeUnauthorized(HttpServletResponse response, String message, AuthErrorType authErrorType)
        throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.fail(401, message, authErrorType.name());
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}

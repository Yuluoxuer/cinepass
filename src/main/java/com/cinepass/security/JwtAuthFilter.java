package com.cinepass.security;

import cn.hutool.json.JSONUtil;
import com.cinepass.common.AuthTokenAttributes;
import com.cinepass.common.Result;
import com.cinepass.common.ResultCode;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.model.UserAccount;
import com.cinepass.service.AuthSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;
    private final UserAccountMapper userAccountMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, AuthSessionService authSessionService,
                         UserAccountMapper userAccountMapper) {
        this.jwtUtil = jwtUtil;
        this.authSessionService = authSessionService;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        JwtUtil.JwtUserInfo userInfo = jwtUtil.parseAccessTokenAllowExpired(token);
        if (userInfo == null || !jwtUtil.isSignatureValid(token)) {
            // 公开路径放行，避免无效 token 阻止公开接口访问
            if (isPublicPath(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            writeUnauthorized(response);
            return;
        }
        if (authSessionService.isDenied(userInfo.getJti())) {
            writeUnauthorized(response);
            return;
        }

        boolean expired = jwtUtil.isExpired(token);
        boolean logoutPath = isLogout(request);

        if (expired && !logoutPath) {
            Optional<AuthSessionService.RefreshSession> refresh =
                    authSessionService.getRefresh(userInfo.getSid());
            if (!refresh.isPresent()) {
                writeUnauthorized(response);
                return;
            }
            AuthSessionService.RefreshSession rs = refresh.get();
            UserAccount account = userAccountMapper.findById(rs.getUserId());
            if (account == null || account.getStatus() == null || account.getStatus() != 1) {
                writeUnauthorized(response);
                return;
            }
            String newToken = jwtUtil.generateAccessToken(
                    account.getUserId(), account.getNickname(), account.getRole(), userInfo.getSid());
            request.setAttribute(AuthTokenAttributes.RENEWED_ACCESS_TOKEN, newToken);
            userInfo = jwtUtil.parseAccessToken(newToken);
            if (userInfo == null) {
                writeUnauthorized(response);
                return;
            }
        }

        try {
            String role = userInfo.getRole();
            SecurityContext.set(userInfo.getUserId(), userInfo.getUsername(),
                    null, null, userInfo.getRoles(), null);
            SecurityContext.setSession(userInfo.getSid(), userInfo.getJti(), role);

            List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + role));
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userInfo.getUserId(), null, authorities);
            authentication.setDetails(userInfo);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } finally {
            SecurityContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isLogout(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "POST".equalsIgnoreCase(request.getMethod())
                && (uri.endsWith("/api/v1/auth/logout") || uri.endsWith("/auth/logout"));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)) {
            return uri.startsWith("/api/v1/movies")
                    || uri.startsWith("/api/v1/cinemas")
                    || uri.startsWith("/api/v1/shows")
                    || uri.startsWith("/api/v1/reco/")
                    || uri.startsWith("/api/v1/tickets/verify")
                    || uri.contains("/pay-session")
                    || uri.contains("/pay-qrcode");
        }
        return uri.contains("/pay-session") || uri.contains("/pay-qrcode");
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSONUtil.toJsonStr(Result.fail(ResultCode.UNAUTHORIZED_TOKEN)));
    }
}

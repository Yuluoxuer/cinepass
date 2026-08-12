package com.cinepass.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 为 {@code /uploads/**} 强制 {@code X-Content-Type-Options: nosniff}，降低 MIME 嗅探 XSS 风险。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class UploadsSecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String uploadsPrefix = context + "/uploads/";
        if (path != null && path.startsWith(uploadsPrefix)) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            if (!response.containsHeader("Content-Disposition")) {
                response.setHeader("Content-Disposition", "inline");
            }
        }
        filterChain.doFilter(request, response);
    }
}

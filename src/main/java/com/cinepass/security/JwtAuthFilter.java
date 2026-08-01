package com.minihr.security; // 权限包

import cn.hutool.json.JSONUtil;                    // Hutool：对象转 JSON 字符串
import com.minihr.common.Result;                   // 统一响应体 {code,message,data}
import com.minihr.common.ResultCode;               // 业务/鉴权错误码枚举
import lombok.extern.slf4j.Slf4j;                  // 生成 log 字段（保留扩展）
import org.springframework.http.MediaType;         // Content-Type 常量
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; // 已认证身份载体
import org.springframework.security.core.authority.SimpleGrantedAuthority; // ROLE_* 权限
import org.springframework.security.core.context.SecurityContextHolder; // Spring 认证上下文
import org.springframework.util.StringUtils;       // 字符串非空判断
import org.springframework.web.filter.OncePerRequestFilter; // 每请求执行一次

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets; // UTF-8，避免中文错误信息乱码
import java.util.Collections;
import java.util.List;

/**
 * JWT 认证过滤器：从 Authorization 头取出 Bearer Token，验签并写入登录态。
 * <ul>
 *   <li>无 Token → 不拦截，交给 SecurityConfig 判断是否公开接口</li>
 *   <li>有 Token 且合法 → 写入自定义 SecurityContext + Spring Authentication</li>
 *   <li>有 Token 但非法/过期 → 直接 401，不再进 Controller</li>
 * </ul>
 * 调用方：SecurityConfig L112 {@code new JwtAuthFilter(jwtUtil)}。覆盖已有文件加注释。
 * 用户指令：「给代码每行加上详细的注释」
 */
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 签发/验签工具，由 SecurityConfig 注入 */
    private final JwtUtil jwtUtil;

    /** Authorization 头标准前缀，例如：Bearer eyJhbGciOi... */
    private static final String BEARER_PREFIX = "Bearer ";

    /** @param jwtUtil 用于 validateToken / parseAccessToken */
    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil; // 保存引用
    }

    /** 过滤器主逻辑：每个请求执行一次。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 浏览器跨域预检 OPTIONS 不带业务 Token，直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response); // 交给后续过滤器
            return; // 本过滤器结束
        }

        // 从 Header 抽出纯 JWT 字符串（去掉 "Bearer "）
        String token = extractToken(request);

        // 没有 Token：可能是公开接口，也可能是未登录；此处不 401，由路径规则决定
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 有 Token 但验签失败或已过期 → 统一 401
        if (!jwtUtil.validateToken(token)) {
            writeUnauthorized(response);
            return; // 不再往下传
        }

        // 解析出 userId / role / sid / jti 等业务字段
        JwtUtil.JwtUserInfo userInfo = jwtUtil.parseAccessToken(token);
        if (userInfo == null) { // 解析异常时 JwtUtil 返回 null
            writeUnauthorized(response);
            return;
        }

        try {
            // 取出角色字符串，如 user / staff / admin
            String role = userInfo.getRole();

            // 写入项目自定义上下文，供 Service 里 SecurityContext.getCurrentUserId() 使用
            // employeeId、departmentId、permissions 票务场景暂不用，传 null
            SecurityContext.set(userInfo.getUserId(), userInfo.getUsername(),
                    null, null, userInfo.getRoles(), null);

            // 补写会话字段：sid（静默续期索引）、jti（单 Token 标识）、role
            SecurityContext.setSession(userInfo.getSid(), userInfo.getJti(), role);

            // Spring Security 要求权限名带 ROLE_ 前缀，hasRole('admin') 才会匹配 ROLE_admin
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + role));

            // principal 放 userId，后续 Authentication.getPrincipal() 可取到
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userInfo.getUserId(), // 身份主体
                            null,                 // 不在上下文中保留原始 Token
                            authorities);         // 角色权限
            authentication.setDetails(userInfo); // 额外细节挂上完整 JwtUserInfo

            // 写入 Spring 官方上下文，@PreAuthorize / hasRole 依赖它
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 认证信息已就绪，进入 Controller
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束必须清理 ThreadLocal，防止线程池复用导致「串用户」
            SecurityContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 从 Authorization 头提取 JWT。
     *
     * @return 纯 Token；没有合法 Bearer 头时返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization"); // 读请求头
        // 必须存在且以 "Bearer " 开头（注意末尾空格）
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()); // 去掉前缀，剩下 JWT
        }
        return null; // 不符合格式视为无 Token
    }

    /** 向客户端写 401 JSON（与全局 Result 包络一致）。 */
    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // HTTP 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // application/json
        response.setCharacterEncoding(StandardCharsets.UTF_8.name()); // UTF-8
        // Body：{"code":40101,"message":"未认证或令牌无效","data":null}
        response.getWriter().write(JSONUtil.toJsonStr(Result.fail(ResultCode.UNAUTHORIZED_TOKEN)));
    }
}

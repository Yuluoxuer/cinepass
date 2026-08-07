package com.cinepass.security;

import cn.hutool.json.JSONUtil;
import com.cinepass.common.Result;
import com.cinepass.common.ResultCode;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.service.AuthSessionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Spring Security 配置：JWT 无状态认证、路径级角色规则、方法级 {@code @PreAuthorize}。
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final AuthSessionService authSessionService;
    private final UserAccountMapper userAccountMapper;

    public SecurityConfig(JwtUtil jwtUtil, AuthSessionService authSessionService,
                          UserAccountMapper userAccountMapper) {
        this.jwtUtil = jwtUtil;
        this.authSessionService = authSessionService;
        this.userAccountMapper = userAccountMapper;
    }

    /** 注册 JWT 认证过滤器 Bean */
    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtUtil, authSessionService, userAccountMapper);
    }

    /** 配置跨域：开发期允许任意 Origin，带 Cookie */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 配置过滤器链：公开路径、后台路径角色门槛、401/403 JSON 响应、挂载 JWT Filter。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write(
                                    JSONUtil.toJsonStr(Result.fail(ResultCode.UNAUTHORIZED_TOKEN)));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.getWriter().write(
                                    JSONUtil.toJsonStr(Result.fail(ResultCode.FORBIDDEN_PERMISSION)));
                        }))
                .authorizeHttpRequests(auth -> auth
                        .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .antMatchers(
                                "/api/v1/auth/login",
                                "/api/v1/auth/register",
                                "/api/v1/auth/password/change",
                                "/api/auth/login",
                                "/doc.html", "/webjars/**",
                                "/v2/api-docs/**", "/v3/api-docs/**",
                                "/swagger-resources/**", "/swagger-ui/**",
                                "/favicon.ico"
                        ).permitAll()
                        .antMatchers(HttpMethod.GET,
                                "/api/v1/movies", "/api/v1/movies/**",
                                "/api/v1/cinemas", "/api/v1/cinemas/**",
                                "/api/v1/shows", "/api/v1/shows/**",
                                "/api/v1/search", "/api/v1/search/**",
                                "/api/v1/tickets/verify",
                                "/uploads/**"
                        ).permitAll()
                        .antMatchers("/api/v1/reco", "/api/v1/reco/**").permitAll()
                        .antMatchers("/api/v1/booking-drafts", "/api/v1/booking-drafts/**").permitAll()
                        // 手机 H5 扫码拉摘要 / 确认支付/核销：免登录，靠二维码内签名 token 或本人 JWT 鉴权
                        .antMatchers(HttpMethod.GET, "/api/v1/orders/*/pay-session").permitAll()
                        .antMatchers(HttpMethod.GET, "/api/v1/orders/*/redeem-session").permitAll()
                        .antMatchers(HttpMethod.POST, "/api/v1/orders/*/pay").permitAll()
                        .antMatchers(HttpMethod.POST, "/api/v1/orders/*/redeem").permitAll()
                        // 后台接口：至少 staff；更细粒度由 @Admin / @Staff 控制
                        .antMatchers("/api/v1/admin/**").hasAnyRole(Roles.STAFF, Roles.ADMIN)
                        .antMatchers("/api/v1/seat-maps", "/api/v1/seat-maps/**")
                        .hasAnyRole(Roles.STAFF, Roles.ADMIN)
                        .antMatchers(HttpMethod.POST, "/api/v1/halls")
                        .hasAnyRole(Roles.STAFF, Roles.ADMIN)
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 密码编码器（BCrypt，强度 10） */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}

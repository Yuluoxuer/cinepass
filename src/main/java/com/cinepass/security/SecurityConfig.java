package com.minihr.security;

import cn.hutool.json.JSONUtil;
import com.minihr.common.Result;
import com.minihr.common.ResultCode;
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
 * Spring Security 总配置（系分 §10）。
 * Agent 对话消息落在 ticket-agent 自有库，中台不再提供 X-Internal-Api-Key / AppendMessages。
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

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
                                "/api/v1/reco/**",
                                "/api/v1/tickets/verify"
                        ).permitAll()
                        .antMatchers(HttpMethod.GET, "/api/v1/orders/*/pay-session").permitAll()
                        .antMatchers(HttpMethod.GET, "/api/v1/orders/*/pay-qrcode").permitAll()
                        .antMatchers("/api/v1/admin/**").hasAnyRole(Roles.STAFF, Roles.ADMIN)
                        .antMatchers("/api/v1/seat-maps", "/api/v1/seat-maps/**")
                        .hasAnyRole(Roles.STAFF, Roles.ADMIN)
                        .antMatchers(HttpMethod.POST, "/api/v1/halls")
                        .hasAnyRole(Roles.STAFF, Roles.ADMIN)
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtUtil),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}

package com.cinepass.config;

import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.mapper.UserProfileMapper;
import com.cinepass.model.UserAccount;
import com.cinepass.model.UserProfile;
import com.cinepass.security.Roles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private static final String DEMO_CINEMA_ID = "c_demo_01";

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserAccountMapper userAccountMapper,
                            UserProfileMapper userProfileMapper,
                            PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed("u00000000000000000000000000000001", "演示用户甲", "13800000001", Roles.USER, "demo123456", null);
        seed("u00000000000000000000000000000002", "运营小李", "13900000002", Roles.STAFF, "ChangeMe123", DEMO_CINEMA_ID);
        seed("u00000000000000000000000000000003", "系统管理员", "13900000001", Roles.ADMIN, "Admin12345", null);
    }

    private void seed(String userId, String nickname, String phone, String role, String rawPassword, String cinemaId) {
        if (userAccountMapper.findByNickname(nickname) != null || userAccountMapper.findByPhone(phone) != null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        UserAccount user = new UserAccount();
        user.setUserId(userId);
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setCinemaId(cinemaId);
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setPreferGenresJson("[]");
        profile.setUpdatedAt(now);
        userProfileMapper.upsert(profile);
        log.info("[DataInitializer] seeded user nickname={} role={}", nickname, role);
    }
}

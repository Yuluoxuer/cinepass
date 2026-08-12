package com.cinepass.service;

import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.LoginDTO;
import com.cinepass.dto.PasswordChangeDTO;
import com.cinepass.dto.RegisterDTO;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.mapper.UserProfileMapper;
import com.cinepass.model.UserAccount;
import com.cinepass.security.JwtUtil;
import com.cinepass.security.Roles;
import com.cinepass.service.impl.AuthServiceImpl;
import com.cinepass.vo.AuthMeVO;
import com.cinepass.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 边界单元测试（模式 D）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String USER_ID = "u_auth_1";

    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthSessionService authSessionService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void login_unknownAccount_shouldUnauthorizedWithUnifiedMessage() {
        when(userAccountMapper.findByNickname("nobody")).thenReturn(null);
        LoginDTO dto = new LoginDTO();
        dto.setAccount("nobody");
        dto.setPassword("password1");
        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void login_disabledAccount_shouldUnauthorized() {
        UserAccount user = activeUser();
        user.setStatus(0);
        when(userAccountMapper.findByNickname("alice")).thenReturn(user);
        when(passwordEncoder.matches("password1", "HASH")).thenReturn(true);
        LoginDTO dto = new LoginDTO();
        dto.setAccount("alice");
        dto.setPassword("password1");
        assertBiz(() -> authService.login(dto), ResultCode.UNAUTHORIZED);
    }

    @Test
    void login_wrongPassword_shouldUnauthorized() {
        when(userAccountMapper.findByNickname("alice")).thenReturn(activeUser());
        when(passwordEncoder.matches("bad", "HASH")).thenReturn(false);
        LoginDTO dto = new LoginDTO();
        dto.setAccount("alice");
        dto.setPassword("bad");
        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误")
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    @Test
    void login_byPhone_shouldIssueTokenWithCinema() {
        UserAccount staff = activeUser();
        staff.setRole(Roles.STAFF);
        staff.setCinemaId("c12");
        staff.setPhone("13812345678");
        when(userAccountMapper.findByPhone("13812345678")).thenReturn(staff);
        when(passwordEncoder.matches("password1", "HASH")).thenReturn(true);
        when(jwtUtil.generateAccessToken(eq(USER_ID), eq("alice"), eq(Roles.STAFF), anyString(), eq("c12")))
                .thenReturn("tok");
        when(jwtUtil.getExpiresInSeconds()).thenReturn(3600L);

        LoginDTO dto = new LoginDTO();
        dto.setAccount("13812345678");
        dto.setPassword("password1");
        LoginVO vo = authService.login(dto);

        assertThat(vo.getAccessToken()).isEqualTo("tok");
        assertThat(vo.getRole()).isEqualTo(Roles.STAFF);
        assertThat(vo.getCinemaId()).isEqualTo("c12");
        verify(authSessionService).saveRefresh(anyString(), eq(USER_ID), eq(Roles.STAFF));
    }

    @Test
    void register_duplicateNickname_shouldConflict() {
        when(userAccountMapper.findByNickname("dup")).thenReturn(activeUser());
        RegisterDTO dto = new RegisterDTO();
        dto.setNickname("dup");
        dto.setPhone("13900001111");
        dto.setPassword("password1");
        assertBiz(() -> authService.register(dto), ResultCode.CONFLICT);
    }

    @Test
    void register_duplicatePhone_shouldConflict() {
        when(userAccountMapper.findByNickname("new")).thenReturn(null);
        when(userAccountMapper.findByPhone("13900001111")).thenReturn(activeUser());
        RegisterDTO dto = new RegisterDTO();
        dto.setNickname("new");
        dto.setPhone("13900001111");
        dto.setPassword("password1");
        assertBiz(() -> authService.register(dto), ResultCode.CONFLICT);
    }

    @Test
    void register_happyPath_shouldTrimNicknameAndCreateProfile() {
        when(userAccountMapper.findByNickname("  bob  ")).thenReturn(null);
        when(userAccountMapper.findByPhone("13900002222")).thenReturn(null);
        when(passwordEncoder.encode("password1")).thenReturn("HASH");
        when(userAccountMapper.insert(any(UserAccount.class))).thenReturn(1);
        when(userProfileMapper.upsert(any())).thenReturn(1);
        when(jwtUtil.generateAccessToken(anyString(), eq("bob"), eq(Roles.USER), anyString(), isNull()))
                .thenReturn("tok");
        when(jwtUtil.getExpiresInSeconds()).thenReturn(3600L);

        RegisterDTO dto = new RegisterDTO();
        dto.setNickname("  bob  ");
        dto.setPhone("13900002222");
        dto.setPassword("password1");
        LoginVO vo = authService.register(dto);

        ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(cap.capture());
        assertThat(cap.getValue().getNickname()).isEqualTo("bob");
        assertThat(cap.getValue().getRole()).isEqualTo(Roles.USER);
        assertThat(vo.getAccessToken()).isEqualTo("tok");
        verify(userProfileMapper).upsert(any());
    }

    @Test
    void changePassword_noAccount_shouldParamError() {
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setOldPassword("oldpass12");
        dto.setNewPassword("newpass12");
        assertBiz(() -> authService.changePassword(null, dto), ResultCode.PARAM_ERROR);
    }

    @Test
    void changePassword_wrongOld_shouldUnauthorized() {
        when(userAccountMapper.findById(USER_ID)).thenReturn(activeUser());
        when(passwordEncoder.matches("wrongold1", "HASH")).thenReturn(false);
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setOldPassword("wrongold1");
        dto.setNewPassword("newpass12");
        assertBiz(() -> authService.changePassword(USER_ID, dto), ResultCode.PASSWORD_WRONG);
    }

    @Test
    void changePassword_sameAsOld_shouldParamError() {
        when(userAccountMapper.findById(USER_ID)).thenReturn(activeUser());
        when(passwordEncoder.matches("password1", "HASH")).thenReturn(true);
        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setOldPassword("password1");
        dto.setNewPassword("password1");
        assertBiz(() -> authService.changePassword(USER_ID, dto), ResultCode.PASSWORD_SAME_AS_OLD);
    }

    @Test
    void changePassword_happyPath_shouldUpdateHash() {
        when(userAccountMapper.findById(USER_ID)).thenReturn(activeUser());
        when(passwordEncoder.matches("oldpass12", "HASH")).thenReturn(true);
        when(passwordEncoder.encode("newpass12")).thenReturn("NEW");
        when(userAccountMapper.update(any(UserAccount.class))).thenReturn(1);

        PasswordChangeDTO dto = new PasswordChangeDTO();
        dto.setOldPassword("oldpass12");
        dto.setNewPassword("newpass12");
        authService.changePassword(USER_ID, dto);

        ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).update(cap.capture());
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("NEW");
    }

    @Test
    void me_missing_shouldNotFound() {
        when(userAccountMapper.findById(USER_ID)).thenReturn(null);
        assertBiz(() -> authService.me(USER_ID), ResultCode.NOT_FOUND);
    }

    @Test
    void me_shouldMaskPhone() {
        UserAccount user = activeUser();
        user.setPhone("13812345678");
        when(userAccountMapper.findById(USER_ID)).thenReturn(user);
        AuthMeVO vo = authService.me(USER_ID);
        assertThat(vo.getPhone()).isEqualTo("138****5678");
        assertThat(vo.getNickname()).isEqualTo("alice");
    }

    @Test
    void logout_shouldDenyJtiAndDeleteRefresh() {
        when(jwtUtil.getExpiresInSeconds()).thenReturn(3600L);
        authService.logout("sid-1", "jti-1");
        verify(authSessionService).deleteRefresh("sid-1");
        verify(authSessionService).denyJti(eq("jti-1"), anyLong());
    }

    private static UserAccount activeUser() {
        UserAccount u = new UserAccount();
        u.setUserId(USER_ID);
        u.setNickname("alice");
        u.setPasswordHash("HASH");
        u.setRole(Roles.USER);
        u.setStatus(1);
        u.setCreatedAt(OffsetDateTime.now());
        return u;
    }

    private static void assertBiz(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(code.getCode()));
    }
}

package com.cinepass.service;

import com.cinepass.common.BusinessException;
import com.cinepass.common.ResultCode;
import com.cinepass.dto.AdminUserCreateDTO;
import com.cinepass.dto.AdminUserUpdateDTO;
import com.cinepass.mapper.UserAccountMapper;
import com.cinepass.mapper.UserProfileMapper;
import com.cinepass.model.UserAccount;
import com.cinepass.security.Roles;
import com.cinepass.security.SecurityContext;
import com.cinepass.service.impl.AdminUserServiceImpl;
import com.cinepass.vo.AdminUserVO;
import com.cinepass.vo.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminUserService 边界单元测试（模式 D）。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final String ADMIN_ID = "u_admin";
    private static final String TARGET_ID = "u_target";

    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    @BeforeEach
    void setUp() {
        SecurityContext.clear();
        SecurityContext.set(ADMIN_ID, "系统管理员", null, null,
                Collections.singletonList(Roles.ADMIN), null);
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    // ---------- page ----------

    @Test
    void page_boundaries_shouldNormalizePageAndSize() {
        when(userAccountMapper.count(isNull(), isNull())).thenReturn(0L);
        when(userAccountMapper.page(isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(Collections.<UserAccount>emptyList());

        PageResult<AdminUserVO> p0 = adminUserService.page(null, null, 0, 0);
        assertThat(p0.getPage()).isEqualTo(1);
        assertThat(p0.getSize()).isEqualTo(20);

        when(userAccountMapper.page(isNull(), isNull(), eq(0), eq(50)))
                .thenReturn(Collections.<UserAccount>emptyList());
        PageResult<AdminUserVO> pHuge = adminUserService.page(null, null, -1, 999);
        assertThat(pHuge.getPage()).isEqualTo(1);
        assertThat(pHuge.getSize()).isEqualTo(50);
    }

    @Test
    void page_shouldMaskPhone() {
        UserAccount row = account(TARGET_ID, "alice", "13812345678", Roles.USER, null, 1);
        when(userAccountMapper.count(eq(Roles.USER), eq(1))).thenReturn(1L);
        when(userAccountMapper.page(eq(Roles.USER), eq(1), eq(0), eq(10)))
                .thenReturn(Collections.singletonList(row));

        PageResult<AdminUserVO> page = adminUserService.page(Roles.USER, 1, 1, 10);
        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems().get(0).getPhone()).isEqualTo("138****5678");
        assertThat(page.getItems().get(0).getNickname()).isEqualTo("alice");
    }

    // ---------- create ----------

    @Test
    void create_duplicateNickname_shouldConflict() {
        AdminUserCreateDTO dto = createDto("dup", "13900001111", Roles.USER, null);
        when(userAccountMapper.findByNickname("dup")).thenReturn(account(TARGET_ID, "dup", null, Roles.USER, null, 1));
        assertBiz(() -> adminUserService.create(dto), ResultCode.CONFLICT);
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
    }

    @Test
    void create_duplicatePhone_shouldConflict() {
        AdminUserCreateDTO dto = createDto("nick", "13900001111", Roles.USER, null);
        when(userAccountMapper.findByNickname("nick")).thenReturn(null);
        when(userAccountMapper.findByPhone("13900001111"))
                .thenReturn(account(TARGET_ID, "other", "13900001111", Roles.USER, null, 1));
        assertBiz(() -> adminUserService.create(dto), ResultCode.CONFLICT);
    }

    @Test
    void create_staffWithoutCinema_shouldParamError() {
        AdminUserCreateDTO dto = createDto("staff1", "13900002222", Roles.STAFF, null);
        when(userAccountMapper.findByNickname("staff1")).thenReturn(null);
        when(userAccountMapper.findByPhone("13900002222")).thenReturn(null);
        assertBiz(() -> adminUserService.create(dto), ResultCode.PARAM_ERROR);
    }

    @Test
    void create_staffBlankCinema_shouldParamError() {
        AdminUserCreateDTO dto = createDto("staff1", null, Roles.STAFF, "  ");
        when(userAccountMapper.findByNickname("staff1")).thenReturn(null);
        assertBiz(() -> adminUserService.create(dto), ResultCode.PARAM_ERROR);
    }

    @Test
    void create_adminWithCinema_shouldParamError() {
        AdminUserCreateDTO dto = createDto("adm2", null, Roles.ADMIN, "c12");
        when(userAccountMapper.findByNickname("adm2")).thenReturn(null);
        assertBiz(() -> adminUserService.create(dto), ResultCode.PARAM_ERROR);
    }

    @Test
    void create_userWithCinema_shouldParamError() {
        AdminUserCreateDTO dto = createDto("u1", null, Roles.USER, "c12");
        when(userAccountMapper.findByNickname("u1")).thenReturn(null);
        assertBiz(() -> adminUserService.create(dto), ResultCode.PARAM_ERROR);
    }

    @Test
    void create_staff_shouldBindCinemaAndEncodePassword() {
        when(userAccountMapper.findByNickname("  staff_ok  ")).thenReturn(null);
        when(passwordEncoder.encode("StaffPass1")).thenReturn("HASH");
        when(userAccountMapper.insert(any(UserAccount.class))).thenReturn(1);
        when(userProfileMapper.upsert(any())).thenReturn(1);

        AdminUserCreateDTO dto = createDto("  staff_ok  ", null, Roles.STAFF, "  c12  ");
        AdminUserVO vo = adminUserService.create(dto);

        ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(cap.capture());
        UserAccount saved = cap.getValue();
        assertThat(saved.getNickname()).isEqualTo("staff_ok");
        assertThat(saved.getCinemaId()).isEqualTo("c12");
        assertThat(saved.getRole()).isEqualTo(Roles.STAFF);
        assertThat(saved.getPasswordHash()).isEqualTo("HASH");
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getUserId()).startsWith("u");
        assertThat(vo.getCinemaId()).isEqualTo("c12");
        verify(userProfileMapper).upsert(any());
    }

    @Test
    void create_admin_shouldClearCinema() {
        when(userAccountMapper.findByNickname("adm_ok")).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        when(userAccountMapper.insert(any(UserAccount.class))).thenReturn(1);
        when(userProfileMapper.upsert(any())).thenReturn(1);

        AdminUserVO vo = adminUserService.create(createDto("adm_ok", "", Roles.ADMIN, null));
        ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(cap.capture());
        assertThat(cap.getValue().getCinemaId()).isNull();
        assertThat(cap.getValue().getPhone()).isNull();
        assertThat(vo.getRole()).isEqualTo(Roles.ADMIN);
    }

    // ---------- update ----------

    @Test
    void update_missingUser_shouldNotFound() {
        when(userAccountMapper.findById(TARGET_ID)).thenReturn(null);
        assertBiz(() -> adminUserService.update(TARGET_ID, new AdminUserUpdateDTO()), ResultCode.NOT_FOUND);
    }

    @Test
    void update_selfDemote_shouldFail() {
        when(userAccountMapper.findById(ADMIN_ID))
                .thenReturn(account(ADMIN_ID, "系统管理员", null, Roles.ADMIN, null, 1));
        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setRole(Roles.STAFF);
        dto.setCinemaId("c12");
        assertBiz(() -> adminUserService.update(ADMIN_ID, dto), ResultCode.FAIL);
    }

    @Test
    void update_selfDisable_shouldFail() {
        when(userAccountMapper.findById(ADMIN_ID))
                .thenReturn(account(ADMIN_ID, "系统管理员", null, Roles.ADMIN, null, 1));
        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setStatus(0);
        assertBiz(() -> adminUserService.update(ADMIN_ID, dto), ResultCode.FAIL);
    }

    @Test
    void update_lastActiveAdmin_shouldFail() {
        when(userAccountMapper.findById(TARGET_ID))
                .thenReturn(account(TARGET_ID, "adm2", null, Roles.ADMIN, null, 1));
        when(userAccountMapper.countActiveAdmins(TARGET_ID)).thenReturn(0L);
        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setStatus(0);
        assertBiz(() -> adminUserService.update(TARGET_ID, dto), ResultCode.FAIL);
    }

    @Test
    void update_demoteAdminWhenOthersExist_shouldSucceed() {
        when(userAccountMapper.findById(TARGET_ID))
                .thenReturn(account(TARGET_ID, "adm2", null, Roles.ADMIN, null, 1));
        when(userAccountMapper.countActiveAdmins(TARGET_ID)).thenReturn(1L);
        when(userAccountMapper.update(any(UserAccount.class))).thenReturn(1);
        when(userAccountMapper.findById(TARGET_ID))
                .thenReturn(account(TARGET_ID, "adm2", null, Roles.ADMIN, null, 1))
                .thenReturn(account(TARGET_ID, "adm2", null, Roles.USER, null, 1));

        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setRole(Roles.USER);
        dto.setCinemaId("");
        AdminUserVO vo = adminUserService.update(TARGET_ID, dto);
        assertThat(vo.getRole()).isEqualTo(Roles.USER);
        verify(userAccountMapper).countActiveAdmins(TARGET_ID);
    }

    @Test
    void update_demoteStaffToUser_withoutCinemaId_shouldClearCinema() {
        when(userAccountMapper.findById(TARGET_ID))
                .thenReturn(account(TARGET_ID, "st1", null, Roles.STAFF, "c12", 1))
                .thenReturn(account(TARGET_ID, "st1", null, Roles.USER, null, 1));
        when(userAccountMapper.update(any(UserAccount.class))).thenReturn(1);

        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setRole(Roles.USER);
        // 不传 cinemaId：应自动清空，而非 400
        AdminUserVO vo = adminUserService.update(TARGET_ID, dto);

        ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).update(cap.capture());
        assertThat(cap.getValue().getRole()).isEqualTo(Roles.USER);
        assertThat(cap.getValue().getCinemaId()).isNull();
        assertThat(cap.getValue().getUpdateCinemaId()).isTrue();
        assertThat(vo.getRole()).isEqualTo(Roles.USER);
        assertThat(vo.getCinemaId()).isNull();
    }

    @Test
    void update_duplicateNickname_shouldConflict() {
        when(userAccountMapper.findById(TARGET_ID))
                .thenReturn(account(TARGET_ID, "old", null, Roles.USER, null, 1));
        when(userAccountMapper.findByNickname("taken"))
                .thenReturn(account("u_other", "taken", null, Roles.USER, null, 1));
        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setNickname("taken");
        assertBiz(() -> adminUserService.update(TARGET_ID, dto), ResultCode.CONFLICT);
    }

    @Test
    void update_duplicatePhone_shouldConflict() {
        when(userAccountMapper.findById(TARGET_ID))
                .thenReturn(account(TARGET_ID, "old", "13800000001", Roles.USER, null, 1));
        when(userAccountMapper.findByPhone("13900009999"))
                .thenReturn(account("u_other", "other", "13900009999", Roles.USER, null, 1));
        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setPhone("13900009999");
        assertBiz(() -> adminUserService.update(TARGET_ID, dto), ResultCode.CONFLICT);
    }

    @Test
    void update_toStaffWithoutCinema_shouldParamError() {
        when(userAccountMapper.findById(TARGET_ID))
                .thenReturn(account(TARGET_ID, "u1", null, Roles.USER, null, 1));
        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setRole(Roles.STAFF);
        assertBiz(() -> adminUserService.update(TARGET_ID, dto), ResultCode.PARAM_ERROR);
    }

    @Test
    void update_clearPhoneAndSetPassword() {
        UserAccount existing = account(TARGET_ID, "u1", "13800000001", Roles.USER, null, 1);
        when(userAccountMapper.findById(TARGET_ID)).thenReturn(existing)
                .thenReturn(account(TARGET_ID, "u1", null, Roles.USER, null, 1));
        when(passwordEncoder.encode("NewPass12")).thenReturn("NEW_HASH");
        when(userAccountMapper.update(any(UserAccount.class))).thenReturn(1);

        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setPhone("");
        dto.setPassword("NewPass12");
        AdminUserVO vo = adminUserService.update(TARGET_ID, dto);

        ArgumentCaptor<UserAccount> cap = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).update(cap.capture());
        assertThat(cap.getValue().getPhone()).isNull();
        assertThat(cap.getValue().getPasswordHash()).isEqualTo("NEW_HASH");
        assertThat(vo.getPhone()).isNull();
    }

    @Test
    void update_sameNicknameOwn_shouldAllow() {
        when(userAccountMapper.findById(TARGET_ID))
                .thenReturn(account(TARGET_ID, "same", null, Roles.USER, null, 1))
                .thenReturn(account(TARGET_ID, "same", null, Roles.USER, null, 1));
        when(userAccountMapper.findByNickname("same"))
                .thenReturn(account(TARGET_ID, "same", null, Roles.USER, null, 1));
        when(userAccountMapper.update(any(UserAccount.class))).thenReturn(1);

        AdminUserUpdateDTO dto = new AdminUserUpdateDTO();
        dto.setNickname("same");
        AdminUserVO vo = adminUserService.update(TARGET_ID, dto);
        assertThat(vo.getNickname()).isEqualTo("same");
    }

    // ---------- helpers ----------

    private static AdminUserCreateDTO createDto(String nick, String phone, String role, String cinemaId) {
        AdminUserCreateDTO dto = new AdminUserCreateDTO();
        dto.setNickname(nick);
        dto.setPhone(phone);
        dto.setPassword("StaffPass1");
        dto.setRole(role);
        dto.setCinemaId(cinemaId);
        return dto;
    }

    private static UserAccount account(String id, String nick, String phone, String role,
                                       String cinemaId, int status) {
        UserAccount u = new UserAccount();
        u.setUserId(id);
        u.setNickname(nick);
        u.setPhone(phone);
        u.setRole(role);
        u.setCinemaId(cinemaId);
        u.setStatus(status);
        u.setPasswordHash("hash");
        u.setCreatedAt(OffsetDateTime.now());
        return u;
    }

    private static void assertBiz(Runnable action, ResultCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(code.getCode()));
    }
}

package com.hris.admin.controller;

import com.hris.admin.dto.AdminRequestAttachmentDto;
import com.hris.admin.dto.AdminRequestCommentDto;
import com.hris.admin.dto.AdminRequestResponseDto;
import com.hris.admin.dto.AdminRequestStatusHistoryDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.service.AdminRequestQueryService;
import com.hris.admin.service.AdminRequestService;
import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.security.PermissionAuthorizationService;
import com.hris.support.TestAuthenticationFactory;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AdminRequestControllerTest.TestSecurityConfig.class})
class AdminRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private AdminRequestService adminRequestService;
    @MockBean private AdminRequestQueryService adminRequestQueryService;
    @MockBean private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private UserProvisioningService userProvisioningService;
    @MockBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("create draft keeps endpoint path and response shape")
    void createDraftKeepsResponseShape() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AdminRequest request = AdminRequest.builder().id(requestId).requesterUserId(userId).build();
        AdminRequestResponseDto dto = responseDto(requestId, userId, AdminRequestStatus.DRAFT);

        when(adminRequestService.create(any(), eq(userId))).thenReturn(request);
        when(adminRequestQueryService.toDto(request, false)).thenReturn(dto);
        doNothing().when(permissionAuthorizationService).authorizePermissionName(any(), eq("ADMIN_REQUEST_CREATE"));

        mockMvc.perform(post("/api/admin-requests")
                .with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestTypeId": "22222222-2222-2222-2222-222222222222",
                      "subject": "Need document",
                      "description": "Please issue it"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.id").value(requestId.toString()))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("my requests endpoint uses /my path and wrapped page")
    void myRequestsEndpointUsesMyPath() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminRequest request = AdminRequest.builder().id(UUID.randomUUID()).requesterUserId(userId).build();
        AdminRequestResponseDto dto = responseDto(request.getId(), userId, AdminRequestStatus.SUBMITTED);

        when(adminRequestService.getMyRequests(eq(userId), any()))
            .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        when(adminRequestQueryService.toDtoPage(any(PageImpl.class), eq(false)))
            .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));
        doNothing().when(permissionAuthorizationService).authorizePermissionName(any(), eq("ADMIN_REQUEST_READ_OWN"));

        mockMvc.perform(get("/api/admin-requests/my").with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].requestNumber").value("AR-2026-001"));
    }

    @Test
    @DisplayName("submit delegates through permission-gated action endpoint")
    void submitDelegatesThroughPermissionGatedActionEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AdminRequest request = AdminRequest.builder().id(requestId).requesterUserId(userId).build();
        AdminRequestResponseDto dto = responseDto(requestId, userId, AdminRequestStatus.SUBMITTED);

        doNothing().when(permissionAuthorizationService).authorizePermissionName(any(), eq("ADMIN_REQUEST_CREATE"));
        when(adminRequestService.submit(requestId, userId)).thenReturn(request);
        when(adminRequestQueryService.toDto(request, false)).thenReturn(dto);

        mockMvc.perform(post("/api/admin-requests/{id}/submit", requestId)
                .with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("approve uses permission-name authorization and global detail path")
    void approveUsesPermissionNameAuthorization() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        AdminRequest request = AdminRequest.builder().id(requestId).requesterUserId(UUID.randomUUID()).build();
        AdminRequestResponseDto dto = responseDto(requestId, UUID.randomUUID(), AdminRequestStatus.APPROVED);

        doNothing().when(permissionAuthorizationService).authorizePermissionName(any(), eq("ADMIN_REQUEST_APPROVE"));
        when(adminRequestService.approve(requestId, userId)).thenReturn(request);
        when(adminRequestQueryService.toDto(request, true)).thenReturn(dto);

        mockMvc.perform(post("/api/admin-requests/{id}/approve", requestId)
                .with(TestAuthenticationFactory.jwtRequest(userId, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        verify(permissionAuthorizationService).authorizePermissionName(any(), eq("ADMIN_REQUEST_APPROVE"));
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
        }
    }

    private AdminRequestResponseDto responseDto(UUID requestId, UUID requesterUserId, AdminRequestStatus status) {
        return new AdminRequestResponseDto(
            requestId,
            "AR-2026-001",
            UUID.randomUUID(),
            requesterUserId,
            "Jane Requester",
            UUID.randomUUID(),
            "CERT_WORK",
            "Certificate",
            "Need document",
            "Please issue it",
            status,
            Instant.now(),
            null,
            null,
            null,
            null,
            false,
            0L,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            List.<AdminRequestAttachmentDto>of(),
            List.<AdminRequestCommentDto>of(),
            List.<AdminRequestStatusHistoryDto>of()
        );
    }
}

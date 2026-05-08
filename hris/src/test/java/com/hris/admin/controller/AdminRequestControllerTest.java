package com.hris.admin.controller;

import com.hris.admin.dto.AdminRequestResponseDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.enums.AdminRequestStatus;
import com.hris.admin.service.AdminRequestQueryService;
import com.hris.admin.service.AdminRequestService;
import com.hris.auth.service.UserProvisioningService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.leave.enums.UrgencyLevel;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.security.PermissionAuthorizationService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminRequestController.class)
@Import({GlobalExceptionHandler.class, AdminRequestControllerTest.TestSecurityConfig.class})
class AdminRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminRequestService adminRequestService;
    @MockBean
    private AdminRequestQueryService adminRequestQueryService;
    @MockBean
    private PermissionAuthorizationService permissionAuthorizationService;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private UserProvisioningService userProvisioningService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            ((FilterChain) invocation.getArgument(2)).doFilter(
                invocation.getArgument(0),
                invocation.getArgument(1)
            );
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("create preserves endpoint path and returns enriched response shape")
    void createPreservesEndpointPathAndReturnsEnrichedResponseShape() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID requestId = UUID.randomUUID();
        AdminRequest request = AdminRequest.builder()
            .id(requestId)
            .requesterId(userId)
            .submittedAt(Instant.now())
            .build();
        AdminRequestResponseDto dto = new AdminRequestResponseDto(
            requestId,
            userId,
            "Jane Requester",
            UUID.randomUUID(),
            "AR-2026-001",
            "Need document",
            UrgencyLevel.NORMAL,
            AdminRequestStatus.SUBMITTED,
            null,
            null,
            request.getSubmittedAt(),
            null,
            null
        );

        when(adminRequestService.create(any(), eq(userId))).thenReturn(request);
        when(adminRequestQueryService.toDto(request)).thenReturn(dto);

        mockMvc.perform(post("/api/admin-requests")
                .with(user(userId.toString()).roles("EMPLOYEE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestTypeId": "22222222-2222-2222-2222-222222222222",
                      "description": "Need document",
                      "urgencyLevel": "NORMAL"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(requestId.toString()))
            .andExpect(jsonPath("$.data.requesterName").value("Jane Requester"))
            .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("my requests preserves wrapped page response shape")
    void myRequestsPreservesWrappedPageResponseShape() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AdminRequest request = AdminRequest.builder().id(UUID.randomUUID()).requesterId(userId).build();
        AdminRequestResponseDto dto = new AdminRequestResponseDto(
            request.getId(),
            userId,
            "Jane Requester",
            UUID.randomUUID(),
            "AR-2026-001",
            "Need document",
            UrgencyLevel.NORMAL,
            AdminRequestStatus.SUBMITTED,
            null,
            null,
            Instant.now(),
            null,
            null
        );

        when(adminRequestService.getMyRequests(eq(userId), any()))
            .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        when(adminRequestQueryService.toDtoPage(any(PageImpl.class)))
            .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin-requests")
                .with(user(userId.toString()).roles("EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].requesterName").value("Jane Requester"))
            .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("incoming requests delegates through query service and keeps path")
    void incomingRequestsDelegatesThroughQueryServiceAndKeepsPath() throws Exception {
        UUID requesterId = UUID.randomUUID();
        AdminRequest request = AdminRequest.builder().id(UUID.randomUUID()).requesterId(requesterId).build();
        AdminRequestResponseDto dto = new AdminRequestResponseDto(
            request.getId(),
            requesterId,
            "Jane Requester",
            UUID.randomUUID(),
            "AR-2026-009",
            "Need urgent document",
            UrgencyLevel.URGENT,
            AdminRequestStatus.IN_PROGRESS,
            null,
            null,
            Instant.now(),
            null,
            null
        );

        doNothing().when(permissionAuthorizationService)
            .authorize(any(), eq("ADMIN_REQUEST"), eq("PROCESS"));
        when(adminRequestService.getIncoming(any()))
            .thenReturn(new PageImpl<>(List.of(request), PageRequest.of(0, 20), 1));
        when(adminRequestQueryService.toDtoPage(any(PageImpl.class)))
            .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/admin-requests/incoming")
                .with(user(UUID.randomUUID().toString()).roles("HR_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].trackingNumber").value("AR-2026-009"))
            .andExpect(jsonPath("$.data.content[0].requesterName").value("Jane Requester"));

        verify(permissionAuthorizationService)
            .authorize(any(), eq("ADMIN_REQUEST"), eq("PROCESS"));
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
        }
    }
}

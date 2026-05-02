package com.hris.leave.controller;

import com.hris.approval.service.ApprovalViewService;
import com.hris.common.GlobalExceptionHandler;
import com.hris.leave.mapper.LeaveMapper;
import com.hris.leave.repository.LeaveTypeRepository;
import com.hris.leave.service.AttachmentDownload;
import com.hris.leave.service.LeaveRequestService;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.auth.service.UserProvisioningService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LeaveRequestController.class)
@Import({GlobalExceptionHandler.class, LeaveRequestAttachmentControllerTest.TestSecurityConfig.class})
class LeaveRequestAttachmentControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REQUEST_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ATTACHMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaveRequestService leaveRequestService;
    @MockBean
    private LeaveMapper leaveMapper;
    @MockBean
    private ApprovalViewService approvalViewService;
    @MockBean
    private LeaveTypeRepository leaveTypeRepository;
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
    void downloadAttachmentReturnsForbiddenWhenServiceDeniesAccess() throws Exception {
        doThrow(new AccessDeniedException("You are not allowed to access attachments for this leave request"))
            .when(leaveRequestService).downloadAttachment(REQUEST_ID, ATTACHMENT_ID, USER_ID);

        mockMvc.perform(get("/api/leave-requests/{id}/attachments/{attachmentId}", REQUEST_ID, ATTACHMENT_ID)
                .with(user(USER_ID.toString()).roles("EMPLOYEE")))
            .andExpect(status().isForbidden());
    }

    @Test
    void downloadAttachmentReturnsFileWhenAuthorized() throws Exception {
        when(leaveRequestService.downloadAttachment(REQUEST_ID, ATTACHMENT_ID, USER_ID)).thenReturn(
            new AttachmentDownload(
                "medical_note.pdf",
                "application/pdf",
                new InputStreamResource(new ByteArrayInputStream("pdf".getBytes()))
            )
        );

        mockMvc.perform(get("/api/leave-requests/{id}/attachments/{attachmentId}", REQUEST_ID, ATTACHMENT_ID)
                .with(user(USER_ID.toString()).roles("EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"medical_note.pdf\""));
    }

    @Test
    void deleteAttachmentReturnsForbiddenWhenServiceDeniesAccess() throws Exception {
        doThrow(new AccessDeniedException("You are not allowed to remove attachments for this leave request"))
            .when(leaveRequestService).deleteAttachment(REQUEST_ID, ATTACHMENT_ID, USER_ID);

        mockMvc.perform(delete("/api/leave-requests/{id}/attachments/{attachmentId}", REQUEST_ID, ATTACHMENT_ID)
                .with(user(USER_ID.toString()).roles("EMPLOYEE")))
            .andExpect(status().isForbidden());
    }

    @Test
    void deleteAttachmentReturnsOkWhenAuthorized() throws Exception {
        mockMvc.perform(delete("/api/leave-requests/{id}/attachments/{attachmentId}", REQUEST_ID, ATTACHMENT_ID)
                .with(user(USER_ID.toString()).roles("EMPLOYEE")))
            .andExpect(status().isOk());
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

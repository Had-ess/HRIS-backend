package com.hris.organisation.controller;

import com.hris.organisation.dto.WorkScheduleDto;
import com.hris.organisation.service.WorkScheduleService;
import com.hris.security.PermissionAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkScheduleControllerTest {

    @Mock
    private WorkScheduleService workScheduleService;

    @Mock
    private PermissionAuthorizationService permissionAuthorizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new WorkScheduleController(workScheduleService, permissionAuthorizationService)).build();
    }

    @Test
    @DisplayName("returns work schedule lookup data for users with employee permissions")
    void returnsWorkScheduleLookupDataSuccessfully() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(workScheduleService.getAll()).thenReturn(List.of(
            new WorkScheduleDto(scheduleId, "Standard 40h", "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY", 8)
        ));

        mockMvc.perform(get("/api/work-schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(scheduleId.toString()))
            .andExpect(jsonPath("$.data[0].name").value("Standard 40h"))
            .andExpect(jsonPath("$.data[0].hoursPerDay").value(8));

        verify(permissionAuthorizationService).authorizeAnyPermissionName(
            any(), eq("EMPLOYEE_READ"), eq("EMPLOYEE_MANAGE"));
    }

    @Test
    @DisplayName("rejects users without employee permissions")
    void rejectsUsersWithoutEmployeePermissions() {
        // any() (not any(Authentication.class)): standalone MockMvc passes a
        // null Authentication, which typed matchers do not match.
        doThrow(new AccessDeniedException("You do not have permission to perform this action"))
            .when(permissionAuthorizationService)
            .authorizeAnyPermissionName(any(), any(String[].class));

        assertThatThrownBy(() -> mockMvc.perform(get("/api/work-schedules")))
            .hasCauseInstanceOf(AccessDeniedException.class);
    }
}

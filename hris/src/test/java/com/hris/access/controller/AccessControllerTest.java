package com.hris.access.controller;

import com.hris.access.dto.AccessMeResponseDto;
import com.hris.access.dto.AccessPermissionDto;
import com.hris.access.dto.NavigationItemDto;
import com.hris.access.dto.NavigationSectionDto;
import com.hris.access.service.AccessResolutionService;
import com.hris.auth.service.UserProvisioningService;
import com.hris.security.JwtAuthenticationFilter;
import com.hris.common.GlobalExceptionHandler;
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
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccessController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, AccessControllerTest.TestSecurityConfig.class})
class AccessControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessResolutionService accessResolutionService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserProvisioningService userProvisioningService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("/api/access/me resolves profiles and permissions without role authorities")
    void getAccessMeResolvesCurrentUserAccess() throws Exception {
        UUID userId = UUID.randomUUID();
        when(accessResolutionService.resolveAccess(userId)).thenReturn(new AccessMeResponseDto(
            List.of("HR_CONSOLE"),
            List.of(new AccessPermissionDto("ACCESS_PROFILE_READ", "ACCESS_PROFILE", "READ", "GLOBAL")),
            List.of("GLOBAL")
        ));

        mockMvc.perform(get("/api/access/me").with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.profileCodes[0]").value("HR_CONSOLE"))
            .andExpect(jsonPath("$.data.permissions[0].name").value("ACCESS_PROFILE_READ"))
            .andExpect(jsonPath("$.data.scopes[0]").value("GLOBAL"));
    }

    @Test
    @DisplayName("/api/navigation/me resolves menu visibility from local access profiles")
    void getNavigationMeResolvesMenuVisibility() throws Exception {
        UUID userId = UUID.randomUUID();
        when(accessResolutionService.resolveNavigation(userId)).thenReturn(List.of(
            new NavigationSectionDto(
                "WORKSPACE",
                "menu.section.workspace",
                List.of(new NavigationItemDto(
                    "menu.workspace.dashboard",
                    "menu.workspace.dashboard",
                    "WORKSPACE",
                    "/dashboard",
                    "home",
                    10
                ))
            )
        ));

        mockMvc.perform(get("/api/navigation/me").with(TestAuthenticationFactory.jwtRequest(userId, "EMPLOYEE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].code").value("WORKSPACE"))
            .andExpect(jsonPath("$.data[0].items[0].code").value("menu.workspace.dashboard"))
            .andExpect(jsonPath("$.data[0].items[0].route").value("/dashboard"));
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

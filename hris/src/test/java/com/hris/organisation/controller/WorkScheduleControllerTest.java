package com.hris.organisation.controller;

import com.hris.organisation.dto.WorkScheduleDto;
import com.hris.organisation.service.WorkScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkScheduleControllerTest {

    @Mock
    private WorkScheduleService workScheduleService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkScheduleController(workScheduleService)).build();
    }

    @Test
    @DisplayName("returns work schedule lookup data successfully")
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
    }
}

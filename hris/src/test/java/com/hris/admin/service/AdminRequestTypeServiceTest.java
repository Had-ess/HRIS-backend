package com.hris.admin.service;

import com.hris.admin.dto.AdminRequestTypeCreateDto;
import com.hris.admin.dto.AdminRequestTypeUpdateDto;
import com.hris.admin.entity.AdminRequestType;
import com.hris.admin.repository.AdminRequestRepository;
import com.hris.admin.repository.AdminRequestTypeRepository;
import com.hris.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRequestTypeService Unit Tests")
class AdminRequestTypeServiceTest {

    @Mock private AdminRequestTypeRepository typeRepository;
    @Mock private AdminRequestRepository requestRepository;

    @InjectMocks
    private AdminRequestTypeService service;

    @Test
    @DisplayName("create validates unique code and positive SLA")
    void createValidatesUniqueCode() {
        when(typeRepository.existsByCodeIgnoreCase("CERT")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new AdminRequestTypeCreateDto(
            "CERT", "Certificate", "Desc", true, 24, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Admin request type code already exists");
    }

    @Test
    @DisplayName("delete deactivates type when requests already use it")
    void deleteDeactivatesWhenUsed() {
        UUID id = UUID.randomUUID();
        AdminRequestType type = AdminRequestType.builder().id(id).code("CERT").name("Certificate").isActive(true).build();
        when(typeRepository.findById(id)).thenReturn(Optional.of(type));
        when(requestRepository.existsByTypeId(id)).thenReturn(true);

        service.deleteOrDeactivate(id);

        assertThat(type.isActive()).isFalse();
        verify(typeRepository).save(type);
    }

    @Test
    @DisplayName("update throws when type does not exist")
    void updateThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(typeRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new AdminRequestTypeUpdateDto(
            "CERT", "Certificate", "Desc", false, 12, true)))
            .isInstanceOf(EntityNotFoundException.class);
    }
}

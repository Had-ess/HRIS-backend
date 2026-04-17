package com.hris.admin.mapper;
import com.hris.admin.dto.AdminRequestResponseDto;
import com.hris.admin.dto.AdminRequestTypeDto;
import com.hris.admin.entity.AdminRequest;
import com.hris.admin.entity.AdminRequestType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
@Mapper(componentModel = "spring")
public interface AdminRequestMapper {
    AdminRequestResponseDto toDto(AdminRequest request);

    @Mapping(target = "isActive", source = "active")
    AdminRequestTypeDto toTypeDto(AdminRequestType type);
}

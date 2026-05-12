package com.hris.admin.mapper;
import com.hris.admin.dto.AdminRequestTypeDto;
import com.hris.admin.entity.AdminRequestType;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface AdminRequestMapper {
    AdminRequestTypeDto toTypeDto(AdminRequestType type);
}

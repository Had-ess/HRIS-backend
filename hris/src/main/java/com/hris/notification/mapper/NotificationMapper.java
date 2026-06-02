package com.hris.notification.mapper;
import com.hris.notification.dto.NotificationResponseDto;
import com.hris.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
@Mapper(componentModel = "spring")
public interface NotificationMapper {
    // Lombok generates isRead() for the boolean field, which MapStruct reads as the
    // "read" property; map it explicitly onto the DTO's isRead component.
    @Mapping(target = "isRead", source = "read")
    NotificationResponseDto toDto(Notification notification);
}

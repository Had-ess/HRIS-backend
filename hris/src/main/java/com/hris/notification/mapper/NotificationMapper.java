package com.hris.notification.mapper;
import com.hris.notification.dto.NotificationResponseDto;
import com.hris.notification.entity.Notification;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface NotificationMapper { NotificationResponseDto toDto(Notification notification); }

package com.hris.notification.mapper;

import com.hris.notification.dto.NotificationResponseDto;
import com.hris.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-18T15:11:07+0100",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class NotificationMapperImpl implements NotificationMapper {

    @Override
    public NotificationResponseDto toDto(Notification notification) {
        if ( notification == null ) {
            return null;
        }

        UUID id = null;
        UUID userId = null;
        String title = null;
        String body = null;
        Instant createdAt = null;

        id = notification.getId();
        userId = notification.getUserId();
        title = notification.getTitle();
        body = notification.getBody();
        createdAt = notification.getCreatedAt();

        boolean isRead = false;

        NotificationResponseDto notificationResponseDto = new NotificationResponseDto( id, userId, title, body, isRead, createdAt );

        return notificationResponseDto;
    }
}

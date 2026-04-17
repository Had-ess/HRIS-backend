package com.hris.auth.mapper;

import com.hris.auth.dto.UserResponseDto;
import com.hris.auth.entity.User;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-17T21:11:49+0100",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserResponseDto toDto(User user) {
        if ( user == null ) {
            return null;
        }

        UUID id = null;
        String email = null;
        String firstName = null;
        String lastName = null;
        String localePreference = null;
        Instant createdAt = null;
        Instant lastLogin = null;

        id = user.getId();
        email = user.getEmail();
        firstName = user.getFirstName();
        lastName = user.getLastName();
        localePreference = user.getLocalePreference();
        createdAt = user.getCreatedAt();
        lastLogin = user.getLastLogin();

        boolean isActive = false;

        UserResponseDto userResponseDto = new UserResponseDto( id, email, firstName, lastName, localePreference, isActive, createdAt, lastLogin );

        return userResponseDto;
    }
}

package com.hris.auth.mapper;

import com.hris.auth.dto.UserResponseDto;
import com.hris.auth.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponseDto toDto(User user);
}

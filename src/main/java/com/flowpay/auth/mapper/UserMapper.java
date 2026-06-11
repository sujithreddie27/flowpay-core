package com.flowpay.auth.mapper;

import com.flowpay.auth.dto.CreateUserRequest;
import com.flowpay.auth.dto.UserResponse;
import com.flowpay.auth.dto.UserSummaryResponse;
import com.flowpay.auth.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "kycStatus", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "emailVerified", ignore = true)
    @Mapping(target = "phoneVerified", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    User toEntity(CreateUserRequest request);

    UserResponse toResponse(User user);

    UserSummaryResponse toSummaryResponse(User user);
}

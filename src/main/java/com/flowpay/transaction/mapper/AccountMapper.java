package com.flowpay.transaction.mapper;

import com.flowpay.transaction.dto.AccountResponse;
import com.flowpay.transaction.dto.CreateAccountRequest;
import com.flowpay.transaction.entity.Account;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface AccountMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "balance", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    Account toEntity(CreateAccountRequest request);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "balance", target = "availableBalance")
    @Mapping(source = "updatedAt", target = "lastActivityAt")
    AccountResponse toResponse(Account account);
}

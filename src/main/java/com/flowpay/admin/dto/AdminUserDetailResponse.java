package com.flowpay.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowpay.auth.dto.UserResponse;
import com.flowpay.transaction.dto.AccountResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserDetailResponse {

    private UserResponse user;
    private List<AccountResponse> accounts;
    private long totalTransactions;
}

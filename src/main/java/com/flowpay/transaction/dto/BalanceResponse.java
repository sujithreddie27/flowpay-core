package com.flowpay.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
}

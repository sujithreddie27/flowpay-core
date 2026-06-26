package com.flowpay.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.common.exception.GlobalExceptionHandler;
import com.flowpay.common.exception.TransactionNotFoundException;
import com.flowpay.transaction.dto.TransactionReceiptResponse;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.dto.TransactionSummaryResponse;
import com.flowpay.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import com.flowpay.security.JwtTokenProvider;
import com.flowpay.security.CustomUserDetailsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = "ADMIN")
class TransactionHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private final UUID userId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();

    @Nested
    @DisplayName("GET /api/v1/transactions/history")
    class GetTransactionHistory {

        @Test
        @DisplayName("should return paginated history with default params")
        void shouldReturnPaginatedHistory() throws Exception {
            TransactionResponse response = TransactionResponse.builder()
                    .id(transactionId)
                    .referenceId("TXN-HIST001")
                    .amount(new BigDecimal("100.00"))
                    .currency("USD")
                    .status(TransactionStatus.COMPLETED)
                    .type(TransactionType.TRANSFER)
                    .createdAt(OffsetDateTime.now())
                    .build();

            Page<TransactionResponse> page = new PageImpl<>(List.of(response));
            when(transactionService.getTransactionHistory(any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/transactions/history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].referenceId").value("TXN-HIST001"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("should support status filter")
        void shouldSupportStatusFilter() throws Exception {
            Page<TransactionResponse> page = new PageImpl<>(List.of());
            when(transactionService.getTransactionHistory(any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/transactions/history")
                            .param("status", "COMPLETED")
                            .param("type", "TRANSFER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("should support pagination params")
        void shouldSupportPaginationParams() throws Exception {
            Page<TransactionResponse> page = new PageImpl<>(List.of());
            when(transactionService.getTransactionHistory(any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/transactions/history")
                            .param("page", "2")
                            .param("size", "5")
                            .param("sortBy", "amount")
                            .param("sortDirection", "ASC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("should support amount range filter")
        void shouldSupportAmountRangeFilter() throws Exception {
            Page<TransactionResponse> page = new PageImpl<>(List.of());
            when(transactionService.getTransactionHistory(any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/transactions/history")
                            .param("minAmount", "50.00")
                            .param("maxAmount", "500.00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transactions/summary/{userId}")
    class GetTransactionSummary {

        @Test
        @DisplayName("should return summary for date range")
        void shouldReturnSummaryForDateRange() throws Exception {
            OffsetDateTime from = OffsetDateTime.now().minusDays(30);
            OffsetDateTime to = OffsetDateTime.now();

            TransactionSummaryResponse summary = TransactionSummaryResponse.builder()
                    .totalTransactions(15)
                    .completedTransactions(12)
                    .failedTransactions(2)
                    .pendingTransactions(1)
                    .cancelledTransactions(0)
                    .totalAmountSent(new BigDecimal("5000.00"))
                    .totalAmountReceived(new BigDecimal("3000.00"))
                    .totalFees(new BigDecimal("25.00"))
                    .netFlow(new BigDecimal("-2000.00"))
                    .periodStart(from)
                    .periodEnd(to)
                    .transactionsByType(Map.of(TransactionType.TRANSFER, 10L, TransactionType.PAYMENT, 5L))
                    .transactionsByStatus(Map.of(TransactionStatus.COMPLETED, 12L, TransactionStatus.FAILED, 2L))
                    .build();

            when(transactionService.getTransactionSummary(eq(userId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                    .thenReturn(summary);

            mockMvc.perform(get("/api/v1/transactions/summary/{userId}", userId)
                            .param("from", from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                            .param("to", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalTransactions").value(15))
                    .andExpect(jsonPath("$.data.completedTransactions").value(12))
                    .andExpect(jsonPath("$.data.failedTransactions").value(2))
                    .andExpect(jsonPath("$.data.totalAmountSent").value(5000.00))
                    .andExpect(jsonPath("$.data.totalAmountReceived").value(3000.00))
                    .andExpect(jsonPath("$.data.netFlow").value(-2000.00));
        }

        @Test
        @DisplayName("should return 400 when missing required params")
        void shouldReturn400WhenMissingParams() throws Exception {
            mockMvc.perform(get("/api/v1/transactions/summary/{userId}", userId))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/transactions/{id}/receipt")
    class GetTransactionReceipt {

        @Test
        @DisplayName("should return full receipt")
        void shouldReturnFullReceipt() throws Exception {
            TransactionReceiptResponse receipt = TransactionReceiptResponse.builder()
                    .transactionId(transactionId)
                    .referenceId("TXN-RECEIPT001")
                    .status(TransactionStatus.COMPLETED)
                    .type(TransactionType.TRANSFER)
                    .amount(new BigDecimal("250.00"))
                    .currency("USD")
                    .fee(new BigDecimal("1.00"))
                    .totalAmount(new BigDecimal("251.00"))
                    .senderName("John Doe")
                    .senderAccountId(UUID.randomUUID())
                    .senderAccountNumber("******7890")
                    .receiverName("Jane Smith")
                    .receiverAccountId(UUID.randomUUID())
                    .receiverAccountNumber("******4321")
                    .description("Payment for order #123")
                    .initiatedAt(OffsetDateTime.now().minusMinutes(5))
                    .completedAt(OffsetDateTime.now())
                    .build();

            when(transactionService.getTransactionReceipt(transactionId)).thenReturn(receipt);

            mockMvc.perform(get("/api/v1/transactions/{id}/receipt", transactionId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.transactionId").value(transactionId.toString()))
                    .andExpect(jsonPath("$.data.referenceId").value("TXN-RECEIPT001"))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.amount").value(250.00))
                    .andExpect(jsonPath("$.data.totalAmount").value(251.00))
                    .andExpect(jsonPath("$.data.senderName").value("John Doe"))
                    .andExpect(jsonPath("$.data.receiverName").value("Jane Smith"))
                    .andExpect(jsonPath("$.data.senderAccountNumber").value("******7890"))
                    .andExpect(jsonPath("$.data.receiverAccountNumber").value("******4321"));
        }

        @Test
        @DisplayName("should return 404 when transaction not found")
        void shouldReturn404WhenNotFound() throws Exception {
            UUID unknownId = UUID.randomUUID();
            when(transactionService.getTransactionReceipt(unknownId))
                    .thenThrow(new TransactionNotFoundException(unknownId));

            mockMvc.perform(get("/api/v1/transactions/{id}/receipt", unknownId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}

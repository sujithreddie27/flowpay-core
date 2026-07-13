package com.flowpay.transaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpay.common.enums.TransactionStatus;
import com.flowpay.common.enums.TransactionType;
import com.flowpay.common.exception.GlobalExceptionHandler;
import com.flowpay.common.exception.InvalidStateTransitionException;
import com.flowpay.common.exception.PaymentException;
import com.flowpay.common.exception.ResourceNotFoundException;
import com.flowpay.common.exception.TransactionNotFoundException;
import com.flowpay.common.exception.TransactionNotRetryableException;
import com.flowpay.transaction.dto.InitiateTransactionRequest;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.service.BatchTransactionService;
import com.flowpay.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.flowpay.security.JwtTokenProvider;
import com.flowpay.security.CustomUserDetailsService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(roles = "ADMIN")
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private BatchTransactionService batchTransactionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private final UUID transactionId = UUID.randomUUID();
    private final UUID senderAccountId = UUID.randomUUID();
    private final UUID receiverAccountId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID receiverId = UUID.randomUUID();

    private TransactionResponse buildTransactionResponse() {
        return TransactionResponse.builder()
                .id(transactionId)
                .referenceId("TXN-ABC123DEF456")
                .senderId(senderId)
                .receiverId(receiverId)
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .fee(BigDecimal.ZERO)
                .status(TransactionStatus.COMPLETED)
                .type(TransactionType.TRANSFER)
                .description("Payment for services")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/transactions - should initiate payment")
    void shouldInitiatePayment() throws Exception {
        InitiateTransactionRequest request = InitiateTransactionRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(new BigDecimal("250.00"))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .description("Payment for services")
                .idempotencyKey("unique-key-001")
                .build();

        when(transactionService.initiatePayment(any(InitiateTransactionRequest.class)))
                .thenReturn(buildTransactionResponse());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Payment initiated successfully"))
                .andExpect(jsonPath("$.data.referenceId").value("TXN-ABC123DEF456"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.amount").value(250.00));
    }

    @Test
    @DisplayName("POST /api/v1/transactions - should return 400 for invalid request")
    void shouldReturn400ForInvalidRequest() throws Exception {
        InitiateTransactionRequest invalidRequest = InitiateTransactionRequest.builder()
                .senderAccountId(null)
                .receiverAccountId(null)
                .amount(null)
                .currency(null)
                .type(null)
                .build();

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{id} - should return transaction")
    void shouldGetTransactionById() throws Exception {
        when(transactionService.getTransactionById(transactionId))
                .thenReturn(buildTransactionResponse());

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.data.referenceId").value("TXN-ABC123DEF456"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/{id} - should return 404 when not found")
    void shouldReturn404WhenTransactionNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(transactionService.getTransactionById(unknownId))
                .thenThrow(new TransactionNotFoundException(unknownId));

        mockMvc.perform(get("/api/v1/transactions/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/reference/{refId} - should return transaction")
    void shouldGetTransactionByReferenceId() throws Exception {
        when(transactionService.getTransactionByReferenceId("TXN-ABC123DEF456"))
                .thenReturn(buildTransactionResponse());

        mockMvc.perform(get("/api/v1/transactions/reference/{refId}", "TXN-ABC123DEF456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.referenceId").value("TXN-ABC123DEF456"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/user/{userId} - should return paginated transactions")
    void shouldGetTransactionsByUserId() throws Exception {
        Page<TransactionResponse> page = new PageImpl<>(List.of(buildTransactionResponse()));
        when(transactionService.getTransactionsByUserId(eq(senderId), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/transactions/user/{userId}", senderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].referenceId").value("TXN-ABC123DEF456"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/{id}/cancel - should cancel transaction")
    void shouldCancelTransaction() throws Exception {
        TransactionResponse cancelled = buildTransactionResponse();
        cancelled.setStatus(TransactionStatus.CANCELLED);

        when(transactionService.cancelTransaction(transactionId)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/transactions/{id}/cancel", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction cancelled successfully"))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/{id}/cancel - should return error for terminal state")
    void shouldReturn400WhenCancellingCompletedTransaction() throws Exception {
        when(transactionService.cancelTransaction(transactionId))
                .thenThrow(new PaymentException("Cannot cancel transaction in terminal state: COMPLETED", "INVALID_STATE_TRANSITION"));

        mockMvc.perform(post("/api/v1/transactions/{id}/cancel", transactionId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/{id}/retry - should retry failed transaction")
    void shouldRetryFailedTransaction() throws Exception {
        TransactionResponse retried = buildTransactionResponse();
        retried.setStatus(TransactionStatus.COMPLETED);

        when(transactionService.retryTransaction(transactionId)).thenReturn(retried);

        mockMvc.perform(post("/api/v1/transactions/{id}/retry", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction retry initiated successfully"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/{id}/retry - should return 422 when not retryable")
    void shouldReturn422WhenNotRetryable() throws Exception {
        when(transactionService.retryTransaction(transactionId))
                .thenThrow(new TransactionNotRetryableException(transactionId, "Transaction is in COMPLETED state"));

        mockMvc.perform(post("/api/v1/transactions/{id}/retry", transactionId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/{id}/reverse - should reverse completed transaction")
    void shouldReverseCompletedTransaction() throws Exception {
        TransactionResponse reversed = buildTransactionResponse();
        reversed.setStatus(TransactionStatus.REVERSED);

        when(transactionService.reverseTransaction(eq(transactionId), any(String.class)))
                .thenReturn(reversed);

        mockMvc.perform(post("/api/v1/transactions/{id}/reverse", transactionId)
                        .param("reason", "Fraud detected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Transaction reversed successfully"))
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/{id}/reverse - should return 409 for invalid state")
    void shouldReturn409WhenReversingNonCompleted() throws Exception {
        when(transactionService.reverseTransaction(eq(transactionId), any(String.class)))
                .thenThrow(new InvalidStateTransitionException(
                        TransactionStatus.PENDING, TransactionStatus.REVERSED));

        mockMvc.perform(post("/api/v1/transactions/{id}/reverse", transactionId)
                        .param("reason", "Error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/retryable - should return retryable transactions")
    void shouldGetRetryableTransactions() throws Exception {
        TransactionResponse failed = buildTransactionResponse();
        failed.setStatus(TransactionStatus.FAILED);

        when(transactionService.getRetryableTransactions()).thenReturn(List.of(failed));

        mockMvc.perform(get("/api/v1/transactions/retryable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].status").value("FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/transactions/stale/process - should process stale transactions")
    void shouldProcessStalePendingTransactions() throws Exception {
        when(transactionService.processStalePendingTransactions()).thenReturn(3);

        mockMvc.perform(post("/api/v1/transactions/stale/process"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.processedCount").value(3))
                .andExpect(jsonPath("$.message").value("Stale pending transactions processed"));
    }
}

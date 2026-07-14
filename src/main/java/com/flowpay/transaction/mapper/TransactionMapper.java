package com.flowpay.transaction.mapper;

import com.flowpay.auth.entity.User;
import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.dto.TransactionResponse.TransactionPartyResponse;
import com.flowpay.transaction.entity.Account;
import com.flowpay.transaction.entity.Transaction;
import org.mapstruct.*;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TransactionMapper {

    @Mapping(source = "senderAccount.id", target = "accountId")
    @Mapping(source = "transaction", target = "sender", qualifiedByName = "mapSender")
    @Mapping(source = "transaction", target = "recipient", qualifiedByName = "mapRecipient")
    @Mapping(source = "transaction", target = "netAmount", qualifiedByName = "computeNetAmount")
    @Mapping(source = "createdAt", target = "initiatedAt")
    @Mapping(source = "processedAt", target = "processedAt")
    @Mapping(target = "completedAt", expression = "java(transaction.getStatus() == com.flowpay.common.enums.TransactionStatus.COMPLETED ? transaction.getProcessedAt() : null)")
    @Mapping(target = "failedAt", expression = "java(transaction.getStatus() == com.flowpay.common.enums.TransactionStatus.FAILED ? transaction.getProcessedAt() : null)")
    TransactionResponse toResponse(Transaction transaction);

    @Named("mapSender")
    default TransactionPartyResponse mapSender(Transaction transaction) {
        User sender = transaction.getSender();
        Account senderAccount = transaction.getSenderAccount();
        return TransactionPartyResponse.builder()
                .id(sender.getId())
                .name(sender.getFullName())
                .accountNumber(senderAccount != null ? senderAccount.getAccountNumber() : null)
                .email(sender.getEmail())
                .phone(sender.getPhone())
                .build();
    }

    @Named("mapRecipient")
    default TransactionPartyResponse mapRecipient(Transaction transaction) {
        User receiver = transaction.getReceiver();
        Account receiverAccount = transaction.getReceiverAccount();
        return TransactionPartyResponse.builder()
                .id(receiver.getId())
                .name(receiver.getFullName())
                .accountNumber(receiverAccount != null ? receiverAccount.getAccountNumber() : null)
                .email(receiver.getEmail())
                .phone(receiver.getPhone())
                .build();
    }

    @Named("computeNetAmount")
    default BigDecimal computeNetAmount(Transaction transaction) {
        BigDecimal amount = transaction.getAmount();
        BigDecimal fee = transaction.getFee();
        if (amount == null) return null;
        if (fee == null) return amount;
        return amount.subtract(fee);
    }
}

package com.flowpay.transaction.mapper;

import com.flowpay.transaction.dto.TransactionResponse;
import com.flowpay.transaction.entity.Transaction;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TransactionMapper {

    @Mapping(source = "sender.id", target = "senderId")
    @Mapping(source = "receiver.id", target = "receiverId")
    @Mapping(source = "senderAccount.id", target = "senderAccountId")
    @Mapping(source = "receiverAccount.id", target = "receiverAccountId")
    TransactionResponse toResponse(Transaction transaction);
}

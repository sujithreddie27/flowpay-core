package com.flowpay.payments.mapper;

import com.flowpay.payments.dto.CreatePaymentMethodRequest;
import com.flowpay.payments.dto.PaymentMethodResponse;
import com.flowpay.payments.entity.PaymentMethod;
import org.mapstruct.*;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface PaymentMethodMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "isVerified", ignore = true)
    @Mapping(target = "status", ignore = true)
    PaymentMethod toEntity(CreatePaymentMethodRequest request);

    @Mapping(source = "user.id", target = "userId")
    PaymentMethodResponse toResponse(PaymentMethod paymentMethod);
}

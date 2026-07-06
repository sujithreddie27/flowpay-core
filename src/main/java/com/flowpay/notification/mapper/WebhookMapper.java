package com.flowpay.notification.mapper;

import com.flowpay.notification.dto.WebhookConfigResponse;
import com.flowpay.notification.dto.WebhookDeliveryResponse;
import com.flowpay.notification.entity.WebhookConfig;
import com.flowpay.notification.entity.WebhookDelivery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface WebhookMapper {

    WebhookConfigResponse toConfigResponse(WebhookConfig config);

    @Mapping(source = "webhookConfig.id", target = "webhookConfigId")
    WebhookDeliveryResponse toDeliveryResponse(WebhookDelivery delivery);
}

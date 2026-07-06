package com.flowpay.notification.mapper;

import com.flowpay.notification.dto.NotificationPreferenceResponse;
import com.flowpay.notification.dto.NotificationResponse;
import com.flowpay.notification.entity.Notification;
import com.flowpay.notification.entity.NotificationPreference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    NotificationPreferenceResponse toPreferenceResponse(NotificationPreference preference);
}

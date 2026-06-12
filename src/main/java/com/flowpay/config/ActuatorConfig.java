package com.flowpay.config;

import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Collection;
import java.util.List;

/**
 * Configuration for Spring Boot Actuator endpoints.
 * Provides production-ready monitoring and management capabilities.
 */
@Configuration
public class ActuatorConfig {

    /**
     * Configure WebMvc endpoint handler mapping for Actuator endpoints.
     * This ensures Actuator endpoints work properly in containerized environments.
     */
    @Bean
    public WebMvcEndpointHandlerMapping webMvcEndpointHandlerMapping(
            WebEndpointsSupplier webEndpointsSupplier,
            ServletEndpointsSupplier servletEndpointsSupplier,
            ControllerEndpointsSupplier controllerEndpointsSupplier,
            EndpointMediaTypes endpointMediaTypes,
            CorsEndpointProperties corsProperties,
            WebEndpointProperties webEndpointProperties,
            Environment environment) {

        String basePath = webEndpointProperties.getBasePath();
        List<String> exposedEndpoints = webEndpointProperties.getExposure().getInclude();

        Collection<?> endpointCollection = webEndpointsSupplier.getEndpoints();

        return new WebMvcEndpointHandlerMapping(
                endpointCollection,
                endpointMediaTypes,
                corsProperties.toCorsConfiguration(),
                servletEndpointsSupplier.getEndpoints(),
                controllerEndpointsSupplier.getEndpoints(),
                ManagementPortType.get(environment));
    }
}

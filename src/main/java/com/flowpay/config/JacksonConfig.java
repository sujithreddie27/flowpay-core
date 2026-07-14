package com.flowpay.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;

/**
 * Jackson configuration to serialize enums as lowercase and deserialize case-insensitively.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilder jacksonBuilder() {
        return new Jackson2ObjectMapperBuilder()
                .featuresToEnable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .modules(enumLowercaseModule());
    }

    @Bean
    public SimpleModule enumLowercaseModule() {
        SimpleModule module = new SimpleModule("EnumLowercaseModule");

        module.addSerializer(Enum.class, new StdSerializer<>(Enum.class) {
            @Override
            public void serialize(Enum value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value.name().toLowerCase());
            }
        });

        return module;
    }
}

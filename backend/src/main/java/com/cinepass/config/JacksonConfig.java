package com.cinepass.config;

import com.cinepass.util.DateTimeFormats;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * 全局 Jackson 配置（仅影响 HTTP 接口 JSON，不影响 Redis）。
 * <p>与 {@link DateTimeFormats} 契约一致：瞬时 ISO-8601 带偏移，日期 yyyy-MM-dd。
 * 去掉旧的 {@code yyyy-MM-dd HH:mm:ss} LocalDateTime 格式，避免与票务 API 字符串契约冲突。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.timeZone(TimeZone.getTimeZone(DateTimeFormats.ZONE));
            builder.serializerByType(OffsetDateTime.class, OffsetDateTimeSerializer.INSTANCE);
            builder.deserializerByType(OffsetDateTime.class, InstantDeserializer.OFFSET_DATE_TIME);
            builder.serializers(
                    new LocalDateSerializer(DateTimeFormatter.ISO_LOCAL_DATE),
                    new LocalTimeSerializer(DateTimeFormatter.ISO_LOCAL_TIME),
                    new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            builder.deserializers(
                    new LocalDateDeserializer(DateTimeFormatter.ISO_LOCAL_DATE),
                    new LocalTimeDeserializer(DateTimeFormatter.ISO_LOCAL_TIME),
                    new LocalDateTimeDeserializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        };
    }
}

package com.cinepass.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.cinepass.serializer.FieldPermissionSerializer.FieldRule;

/**
 * Jackson 字段权限过滤器（模板桩实现）。
 */
public class FieldPermissionPropertyFilter extends SimpleBeanPropertyFilter implements PropertyFilter {

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen,
                                 SerializerProvider provider, PropertyWriter writer) throws Exception {
        String tableName = pojo.getClass().getSimpleName();
        String fieldName = writer.getName();

        FieldRule rule = FieldPermissionSerializer.getFieldRule(tableName, fieldName);

        if (rule == FieldRule.HIDDEN) {
            return;
        }

        if (rule == FieldRule.MASKED) {
            Object value = writer.getMember().getValue(pojo);
            if (value instanceof String) {
                String masked = FieldPermissionSerializer.mask((String) value, fieldName);
                jgen.writeStringField(fieldName, masked);
                return;
            }
        }

        writer.serializeAsField(pojo, jgen, provider);
    }
}

package com.minihr.serializer;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * 字段权限注解内省器 — 为 POJO 自动注入 fieldPermission filterId。
 */
public class FieldPermissionAnnotationIntrospector extends JacksonAnnotationIntrospector {

    private static final long serialVersionUID = 1L;

    public static final String FIELD_PERMISSION_FILTER_ID = "fieldPermission";

    @Override
    public Object findFilterId(Annotated a) {
        Object existing = super.findFilterId(a);
        if (existing != null) {
            return existing;
        }
        if (a instanceof com.fasterxml.jackson.databind.introspect.AnnotatedClass) {
            return FIELD_PERMISSION_FILTER_ID;
        }
        return null;
    }

    public static AnnotationIntrospector pair(AnnotationIntrospector other) {
        if (other == null) {
            return new FieldPermissionAnnotationIntrospector();
        }
        return new com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair(
                new FieldPermissionAnnotationIntrospector(), other);
    }
}

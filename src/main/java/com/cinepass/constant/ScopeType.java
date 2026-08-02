package com.cinepass.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据范围类型枚举
 */
@Getter
@AllArgsConstructor
public enum ScopeType {

    ALL("ALL", "全部数据"),
    DEPARTMENT_AND_SUB("DEPARTMENT_AND_SUB", "本部门及下属部门"),
    DEPARTMENT("DEPARTMENT", "本部门"),
    SELF("SELF", "仅本人");

    private final String code;
    private final String description;

    public static ScopeType fromCode(String code) {
        for (ScopeType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}

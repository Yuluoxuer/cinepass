package com.cinepass.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 结构化演职人员展示对象，用于 MovieVO 的 castMembers 字段。
 * 数据来源：ES {@code movie} 索引的 cast 字段解析，或 DB cast_text 解析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CastMemberVO {

    /** 演员/导演姓名 */
    private String name;

    /** 饰演角色名（导演时可为 null） */
    private String role;

    /** 头像 URL */
    private String avatarUrl;
}

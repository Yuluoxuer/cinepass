<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# mapper (XML)

## Purpose
MyBatis XML Mapper 映射文件目录。复杂多表 JOIN、动态 SQL 写在此处。

## For AI Agents

### Working In This Directory
- 文件命名与 Mapper 接口同名（`UserMapper.java` → `UserMapper.xml`）
- `namespace` = Mapper 接口完整类名
- 简单 CRUD 不需要 XML，直接用注解

### Common Patterns
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.minihr.mapper.UserMapper">
    <select id="selectByConditions" resultType="com.minihr.entity.SysUser">
        SELECT u.*, d.dept_name
        FROM sys_user u
        LEFT JOIN department d ON u.dept_id = d.id
        <where>
            <if test="username != null">AND u.username LIKE #{username}</if>
        </where>
    </select>
</mapper>
```

<!-- MANUAL: -->

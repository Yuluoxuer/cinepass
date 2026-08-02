package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
public class UserAccount implements Serializable {
    private static final long serialVersionUID = 1L;
    private String userId;
    private String nickname;
    private String phone;
    private String passwordHash;
    private String role;
    private String avatarUrl;
    private Integer status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

package com.cinepass.model;

import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 购票草稿表 {@code agent_session}（中台 BookingDraft 真相源）。
 */
@Data
public class AgentSession implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String userId;
    private String source;
    private String state;
    /** BookingDraft JSON 全文 */
    private String draftJson;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

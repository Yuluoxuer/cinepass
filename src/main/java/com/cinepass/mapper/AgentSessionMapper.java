package com.cinepass.mapper;

import com.cinepass.model.AgentSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * agent_session（BookingDraft）Mapper。
 */
@Mapper
public interface AgentSessionMapper {

    int insert(AgentSession session);

    AgentSession findById(@Param("sessionId") String sessionId);

    AgentSession findByIdForUpdate(@Param("sessionId") String sessionId);

    int updateCas(AgentSession session);
}

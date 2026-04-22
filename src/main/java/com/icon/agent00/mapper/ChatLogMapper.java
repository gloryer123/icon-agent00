package com.icon.agent00.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;

@Mapper
public interface ChatLogMapper {
    @Insert("INSERT INTO chat_log(session_id, user_query, assistant_response) VALUES(#{sessionId}, #{userQuery}, #{assistantResponse})")
    int insertLog(@Param("sessionId") String sessionId, @Param("userQuery") String userQuery, @Param("assistantResponse") String assistantResponse);
}

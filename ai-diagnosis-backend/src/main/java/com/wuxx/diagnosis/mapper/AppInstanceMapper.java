package com.wuxx.diagnosis.mapper;

import com.wuxx.diagnosis.domain.AppInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppInstanceMapper {

    @Select("""
            SELECT
                id,
                app_id AS appId,
                app_name AS appName,
                env,
                ip,
                arthas_http_port AS arthasHttpPort,
                arthas_username AS arthasUsername,
                arthas_password AS arthasPassword,
                arthas_agent_id AS arthasAgentId,
                access_mode AS accessMode,
                status,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM app_instance
            WHERE app_id = #{appId}
              AND env = #{env}
              AND status = 'ONLINE'
            LIMIT 1
            """)
    AppInstance findOnlineByAppIdAndEnv(@Param("appId") String appId, @Param("env") String env);
}

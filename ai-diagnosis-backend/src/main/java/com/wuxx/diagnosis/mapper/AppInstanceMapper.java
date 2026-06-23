package com.wuxx.diagnosis.mapper;

import java.util.List;

import com.wuxx.diagnosis.domain.AppInstance;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
                password_cipher AS passwordCipher,
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

    @Select("""
            SELECT
                id, app_id AS appId, app_name AS appName, env, ip,
                arthas_http_port AS arthasHttpPort, arthas_username AS arthasUsername,
                arthas_password AS arthasPassword, password_cipher AS passwordCipher,
                arthas_agent_id AS arthasAgentId, access_mode AS accessMode, status,
                created_at AS createdAt, updated_at AS updatedAt
            FROM app_instance
            ORDER BY app_name ASC, app_id ASC, env ASC
            """)
    List<AppInstance> findAll();

    @Select("""
            SELECT
                id, app_id AS appId, app_name AS appName, env, ip,
                arthas_http_port AS arthasHttpPort, arthas_username AS arthasUsername,
                arthas_password AS arthasPassword, password_cipher AS passwordCipher,
                arthas_agent_id AS arthasAgentId, access_mode AS accessMode, status,
                created_at AS createdAt, updated_at AS updatedAt
            FROM app_instance WHERE id = #{id}
            """)
    AppInstance findById(@Param("id") Long id);

    @Select("""
            SELECT
                id, app_id AS appId, app_name AS appName, env, ip,
                arthas_http_port AS arthasHttpPort, arthas_username AS arthasUsername,
                arthas_password AS arthasPassword, password_cipher AS passwordCipher,
                arthas_agent_id AS arthasAgentId, access_mode AS accessMode, status,
                created_at AS createdAt, updated_at AS updatedAt
            FROM app_instance
            WHERE app_id = #{appId} AND env = #{env}
            LIMIT 1
            """)
    AppInstance findByAppIdAndEnv(@Param("appId") String appId, @Param("env") String env);

    @Select("""
            SELECT
                id, app_id AS appId, app_name AS appName, env, ip,
                arthas_http_port AS arthasHttpPort, arthas_username AS arthasUsername,
                arthas_password AS arthasPassword, password_cipher AS passwordCipher,
                arthas_agent_id AS arthasAgentId, access_mode AS accessMode, status,
                created_at AS createdAt, updated_at AS updatedAt
            FROM app_instance
            WHERE arthas_agent_id = #{agentId}
            LIMIT 1
            """)
    AppInstance findByArthasAgentId(@Param("agentId") String agentId);

    @Insert("""
            INSERT INTO app_instance (
                app_id, app_name, env, ip, arthas_http_port,
                arthas_username, arthas_password, password_cipher,
                arthas_agent_id, access_mode, status, created_at, updated_at
            ) VALUES (
                #{appId}, #{appName}, #{env}, #{ip}, #{arthasHttpPort},
                #{arthasUsername}, #{arthasPassword}, #{passwordCipher},
                #{arthasAgentId}, #{accessMode}, #{status}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AppInstance instance);

    @Update("""
            UPDATE app_instance
            SET app_id = #{appId}, app_name = #{appName}, env = #{env}, ip = #{ip},
                arthas_http_port = #{arthasHttpPort}, arthas_username = #{arthasUsername},
                arthas_password = #{arthasPassword}, password_cipher = #{passwordCipher},
                arthas_agent_id = #{arthasAgentId}, access_mode = #{accessMode},
                status = #{status}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(AppInstance instance);

    @Delete("DELETE FROM app_instance WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}

package com.wuxx.diagnosis.sql.mapper;

import java.util.List;

import com.wuxx.diagnosis.sql.domain.SqlDatasourceConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SqlDatasourceConfigMapper {

    @Insert("""
            INSERT INTO sql_datasource_config (
                datasource_code, datasource_name, app_id, db_type, jdbc_url, username,
                password_cipher, env, status, created_at, updated_at
            ) VALUES (
                #{datasourceCode}, #{datasourceName}, #{appId}, #{dbType}, #{jdbcUrl}, #{username},
                #{passwordCipher}, #{env}, #{status}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SqlDatasourceConfig config);

    @Update("""
            UPDATE sql_datasource_config
            SET datasource_code=#{datasourceCode}, datasource_name=#{datasourceName},
                app_id=#{appId}, db_type=#{dbType}, jdbc_url=#{jdbcUrl}, username=#{username},
                password_cipher=#{passwordCipher}, env=#{env}, status=#{status}, updated_at=#{updatedAt}
            WHERE id=#{id}
            """)
    int update(SqlDatasourceConfig config);

    @Select("""
            SELECT id, datasource_code AS datasourceCode, datasource_name AS datasourceName,
                   app_id AS appId, db_type AS dbType, jdbc_url AS jdbcUrl, username,
                   password_cipher AS passwordCipher, env, status,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM sql_datasource_config WHERE id=#{id}
            """)
    SqlDatasourceConfig findById(@Param("id") Long id);

    @Select("""
            SELECT id, datasource_code AS datasourceCode, datasource_name AS datasourceName,
                   app_id AS appId, db_type AS dbType, jdbc_url AS jdbcUrl, username,
                   password_cipher AS passwordCipher, env, status,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM sql_datasource_config
            WHERE datasource_code=#{code} AND env=#{env}
            """)
    SqlDatasourceConfig findByCodeAndEnv(@Param("code") String code, @Param("env") String env);

    @Select("""
            SELECT id, datasource_code AS datasourceCode, datasource_name AS datasourceName,
                   app_id AS appId, db_type AS dbType, jdbc_url AS jdbcUrl, username,
                   password_cipher AS passwordCipher, env, status,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM sql_datasource_config ORDER BY env, app_id, datasource_code
            """)
    List<SqlDatasourceConfig> findAll();

    @Select("""
            SELECT id, datasource_code AS datasourceCode, datasource_name AS datasourceName,
                   app_id AS appId, db_type AS dbType, jdbc_url AS jdbcUrl, username,
                   password_cipher AS passwordCipher, env, status,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM sql_datasource_config
            WHERE app_id=#{appId} AND env=#{env} AND status='ENABLED'
            ORDER BY datasource_name
            """)
    List<SqlDatasourceConfig> findEnabledByAppAndEnv(@Param("appId") String appId, @Param("env") String env);

    @Delete("DELETE FROM sql_datasource_config WHERE id=#{id}")
    int deleteById(@Param("id") Long id);
}

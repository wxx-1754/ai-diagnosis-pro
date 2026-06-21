package com.wuxx.diagnosis.sql.mapper;

import java.util.List;

import com.wuxx.diagnosis.sql.domain.SqlDiagnosisRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SqlDiagnosisRecordMapper {

    @Insert("""
            INSERT INTO sql_diagnosis_record (
                task_no, datasource_code, db_type, main_table_name, sql_hash,
                original_sql, normalized_sql, status, created_at, updated_at
            ) VALUES (
                #{taskNo}, #{datasourceCode}, #{dbType}, #{mainTableName}, #{sqlHash},
                #{originalSql}, #{normalizedSql}, #{status}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SqlDiagnosisRecord record);

    @Update("""
            UPDATE sql_diagnosis_record
            SET status=#{status}, explain_sql=#{explainSql}, explain_result=#{explainResult},
                table_meta_json=#{tableMetaJson}, index_meta_json=#{indexMetaJson},
                table_stats_json=#{tableStatsJson}, diagnosis_result=#{diagnosisResult},
                error_message=#{errorMessage}, updated_at=#{updatedAt}
            WHERE id=#{id}
            """)
    int updateResult(SqlDiagnosisRecord record);

    @Select("""
            SELECT id, task_no AS taskNo, datasource_code AS datasourceCode, db_type AS dbType,
                   main_table_name AS mainTableName, sql_hash AS sqlHash, original_sql AS originalSql,
                   normalized_sql AS normalizedSql, explain_sql AS explainSql,
                   explain_result AS explainResult, table_meta_json AS tableMetaJson,
                   index_meta_json AS indexMetaJson, table_stats_json AS tableStatsJson,
                   diagnosis_result AS diagnosisResult, status, error_message AS errorMessage,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM sql_diagnosis_record WHERE id=#{id}
            """)
    SqlDiagnosisRecord findById(@Param("id") Long id);

    @Select("""
            SELECT id, task_no AS taskNo, datasource_code AS datasourceCode, db_type AS dbType,
                   main_table_name AS mainTableName, sql_hash AS sqlHash, original_sql AS originalSql,
                   normalized_sql AS normalizedSql, explain_sql AS explainSql,
                   explain_result AS explainResult, table_meta_json AS tableMetaJson,
                   index_meta_json AS indexMetaJson, table_stats_json AS tableStatsJson,
                   diagnosis_result AS diagnosisResult, status, error_message AS errorMessage,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM sql_diagnosis_record WHERE task_no=#{taskNo} ORDER BY id DESC
            """)
    List<SqlDiagnosisRecord> findByTaskNo(@Param("taskNo") String taskNo);

    @Select("""
            SELECT id, task_no AS taskNo, datasource_code AS datasourceCode, db_type AS dbType,
                   main_table_name AS mainTableName, sql_hash AS sqlHash, original_sql AS originalSql,
                   normalized_sql AS normalizedSql, explain_sql AS explainSql,
                   explain_result AS explainResult, table_meta_json AS tableMetaJson,
                   index_meta_json AS indexMetaJson, table_stats_json AS tableStatsJson,
                   diagnosis_result AS diagnosisResult, status, error_message AS errorMessage,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM sql_diagnosis_record WHERE task_no=#{taskNo} ORDER BY id DESC LIMIT 1
            """)
    SqlDiagnosisRecord findLatestByTaskNo(@Param("taskNo") String taskNo);

    @Select("""
            SELECT COUNT(*) FROM sql_diagnosis_record
            WHERE task_no=#{taskNo} AND status IN ('CREATED','RUNNING')
            """)
    long countActiveByTaskNo(@Param("taskNo") String taskNo);

    @Select("SELECT COUNT(*) FROM sql_diagnosis_record WHERE datasource_code=#{code}")
    long countByDatasourceCode(@Param("code") String code);

    @Delete("DELETE FROM sql_diagnosis_record WHERE task_no=#{taskNo}")
    int deleteByTaskNo(@Param("taskNo") String taskNo);
}

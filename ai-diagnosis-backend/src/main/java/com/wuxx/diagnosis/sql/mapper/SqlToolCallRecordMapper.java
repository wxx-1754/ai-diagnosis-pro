package com.wuxx.diagnosis.sql.mapper;

import com.wuxx.diagnosis.sql.domain.SqlToolCallRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SqlToolCallRecordMapper {

    @Insert("""
            INSERT INTO sql_tool_call_record (
                task_no, sql_record_id, datasource_code, tool_name, request_sql,
                success, cost_millis, result_excerpt, error_message, created_at
            ) VALUES (
                #{taskNo}, #{sqlRecordId}, #{datasourceCode}, #{toolName}, #{requestSql},
                #{success}, #{costMillis}, #{resultExcerpt}, #{errorMessage}, #{createdAt}
            )
            """)
    int insert(SqlToolCallRecord record);

    @Delete("DELETE FROM sql_tool_call_record WHERE task_no=#{taskNo}")
    int deleteByTaskNo(@Param("taskNo") String taskNo);
}

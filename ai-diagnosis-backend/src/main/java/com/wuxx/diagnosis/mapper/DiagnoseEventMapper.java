package com.wuxx.diagnosis.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.wuxx.diagnosis.sse.DiagnoseEvent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DiagnoseEventMapper {

    @Insert("""
            INSERT INTO diagnose_event (
                task_no,
                event_type,
                message,
                command,
                tool_name,
                success,
                data_json,
                created_at
            ) VALUES (
                #{taskNo},
                #{eventType},
                #{message},
                #{command},
                #{toolName},
                #{success},
                #{dataJson},
                #{time}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DiagnoseEvent event);

    @Select("""
            SELECT
                id,
                task_no AS taskNo,
                event_type AS eventType,
                message,
                command,
                tool_name AS toolName,
                success,
                data_json AS dataJson,
                created_at AS time
            FROM diagnose_event
            WHERE task_no = #{taskNo}
              AND id > #{afterEventId}
            ORDER BY id ASC
            """)
    List<DiagnoseEvent> findAfter(@Param("taskNo") String taskNo,
                                  @Param("afterEventId") long afterEventId);

    @Select("""
            SELECT MAX(id)
            FROM diagnose_event
            WHERE task_no = #{taskNo}
            """)
    Long findLastEventId(@Param("taskNo") String taskNo);

    @Select("""
            SELECT MAX(created_at)
            FROM diagnose_event
            WHERE task_no = #{taskNo}
            """)
    LocalDateTime findLastEventTime(@Param("taskNo") String taskNo);

    @Select("""
            SELECT
                id,
                task_no AS taskNo,
                event_type AS eventType,
                message,
                command,
                tool_name AS toolName,
                success,
                data_json AS dataJson,
                created_at AS time
            FROM diagnose_event
            WHERE task_no = #{taskNo}
              AND event_type = #{eventType}
            ORDER BY id DESC
            LIMIT 1
            """)
    DiagnoseEvent findLatestByType(@Param("taskNo") String taskNo,
                                   @Param("eventType") String eventType);

    @Delete("""
            DELETE FROM diagnose_event
            WHERE task_no = #{taskNo}
            """)
    int deleteByTaskNo(@Param("taskNo") String taskNo);
}

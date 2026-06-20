package com.wuxx.diagnosis.mapper;

import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArthasCommandRecordMapper {

    @Insert("""
            INSERT INTO arthas_command_record (
                request_no,
                task_no,
                app_id,
                env,
                command,
                command_type,
                success,
                cost_millis,
                output_excerpt,
                error_message,
                created_at
            ) VALUES (
                #{requestNo},
                #{taskNo},
                #{appId},
                #{env},
                #{command},
                #{commandType},
                #{success},
                #{costMillis},
                #{outputExcerpt},
                #{errorMessage},
                #{createdAt}
            )
            """)
    int insert(ArthasCommandRecord record);

    @Select("""
            SELECT
                id,
                request_no AS requestNo,
                task_no AS taskNo,
                app_id AS appId,
                env,
                command,
                command_type AS commandType,
                success,
                cost_millis AS costMillis,
                output_excerpt AS outputExcerpt,
                error_message AS errorMessage,
                created_at AS createdAt
            FROM arthas_command_record
            WHERE task_no = #{taskNo}
            ORDER BY id ASC
            """)
    List<ArthasCommandRecord> findByTaskNo(@Param("taskNo") String taskNo);

    @Delete("""
            DELETE FROM arthas_command_record
            WHERE task_no = #{taskNo}
            """)
    int deleteByTaskNo(@Param("taskNo") String taskNo);
}

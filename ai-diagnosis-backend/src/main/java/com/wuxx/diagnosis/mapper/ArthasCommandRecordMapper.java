package com.wuxx.diagnosis.mapper;

import com.wuxx.diagnosis.domain.ArthasCommandRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArthasCommandRecordMapper {

    @Insert("""
            INSERT INTO arthas_command_record (
                request_no,
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
}

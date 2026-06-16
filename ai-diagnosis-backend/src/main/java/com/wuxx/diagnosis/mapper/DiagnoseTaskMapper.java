package com.wuxx.diagnosis.mapper;

import com.wuxx.diagnosis.domain.DiagnoseTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DiagnoseTaskMapper {

    @Insert("""
            INSERT INTO diagnose_task (
                task_no,
                app_id,
                env,
                user_id,
                question,
                diagnose_type,
                target_uri,
                target_class,
                target_method,
                status,
                conclusion,
                error_message,
                created_at,
                updated_at
            ) VALUES (
                #{taskNo},
                #{appId},
                #{env},
                #{userId},
                #{question},
                #{diagnoseType},
                #{targetUri},
                #{targetClass},
                #{targetMethod},
                #{status},
                #{conclusion},
                #{errorMessage},
                #{createdAt},
                #{updatedAt}
            )
            """)
    int insert(DiagnoseTask task);

    @Select("""
            SELECT
                id,
                task_no AS taskNo,
                app_id AS appId,
                env,
                user_id AS userId,
                question,
                diagnose_type AS diagnoseType,
                target_uri AS targetUri,
                target_class AS targetClass,
                target_method AS targetMethod,
                status,
                conclusion,
                error_message AS errorMessage,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM diagnose_task
            WHERE task_no = #{taskNo}
            """)
    DiagnoseTask findByTaskNo(@Param("taskNo") String taskNo);

    @Update("""
            UPDATE diagnose_task
            SET status = #{status},
                updated_at = NOW()
            WHERE task_no = #{taskNo}
            """)
    int updateStatus(@Param("taskNo") String taskNo, @Param("status") String status);

    @Update("""
            UPDATE diagnose_task
            SET status = #{status},
                conclusion = #{conclusion},
                error_message = #{errorMessage},
                updated_at = NOW()
            WHERE task_no = #{taskNo}
            """)
    int finishTask(@Param("taskNo") String taskNo,
                   @Param("status") String status,
                   @Param("conclusion") String conclusion,
                   @Param("errorMessage") String errorMessage);
}

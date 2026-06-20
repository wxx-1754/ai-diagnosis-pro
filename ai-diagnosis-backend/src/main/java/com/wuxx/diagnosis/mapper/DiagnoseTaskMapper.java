package com.wuxx.diagnosis.mapper;

import java.util.List;
import java.util.Map;

import com.wuxx.diagnosis.domain.DiagnoseOverviewStats;
import com.wuxx.diagnosis.domain.DiagnoseTask;
import com.wuxx.diagnosis.domain.DiagnoseTaskListItem;
import com.wuxx.diagnosis.domain.DiagnoseTaskQuery;
import org.apache.ibatis.annotations.Delete;
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

    @Delete("""
            DELETE FROM diagnose_task
            WHERE task_no = #{taskNo}
            """)
    int deleteByTaskNo(@Param("taskNo") String taskNo);

    @Update("""
            UPDATE diagnose_task
            SET status = #{status},
                updated_at = NOW()
            WHERE task_no = #{taskNo}
            """)
    int updateStatus(@Param("taskNo") String taskNo, @Param("status") String status);

    @Update("""
            UPDATE diagnose_task
            SET status = 'INTERRUPTED',
                error_message = #{reason},
                updated_at = NOW()
            WHERE task_no = #{taskNo}
              AND status IN ('CREATED', 'RUNNING')
            """)
    int markInterruptedIfActive(@Param("taskNo") String taskNo,
                                @Param("reason") String reason);

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
            WHERE status IN ('CREATED', 'RUNNING')
            """)
    List<DiagnoseTask> findActiveTasks();

    @Update("""
            UPDATE diagnose_task
            SET diagnose_type = #{diagnoseType},
                target_class = #{targetClass},
                target_method = #{targetMethod},
                updated_at = NOW()
            WHERE task_no = #{taskNo}
            """)
    int updateIntent(@Param("taskNo") String taskNo,
                     @Param("diagnoseType") String diagnoseType,
                     @Param("targetClass") String targetClass,
                     @Param("targetMethod") String targetMethod);

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

    @Select("""
            <script>
            SELECT
                task_no AS taskNo,
                app_id AS appId,
                env,
                diagnose_type AS diagnoseType,
                status,
                LEFT(question, 140) AS question,
                target_uri AS targetUri,
                target_class AS targetClass,
                target_method AS targetMethod,
                LEFT(conclusion, 200) AS conclusion,
                LEFT(error_message, 200) AS errorMessage,
                user_id AS userId,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM diagnose_task
            <where>
                <if test="query.appId != null and query.appId != ''">
                    AND app_id = #{query.appId}
                </if>
                <if test="query.env != null and query.env != ''">
                    AND env = #{query.env}
                </if>
                <if test="query.diagnoseType != null and query.diagnoseType != ''">
                    AND diagnose_type = #{query.diagnoseType}
                </if>
                <if test="query.status != null and query.status != ''">
                    AND status = #{query.status}
                </if>
                <if test="query.startTime != null">
                    AND created_at &gt;= #{query.startTime}
                </if>
                <if test="query.endTime != null">
                    AND created_at &lt; #{query.endTime}
                </if>
                <if test="query.keyword != null and query.keyword != ''">
                    AND (task_no LIKE CONCAT(#{query.keyword}, '%')
                         OR question LIKE CONCAT('%', #{query.keyword}, '%'))
                </if>
            </where>
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<DiagnoseTaskListItem> pageQuery(@Param("query") DiagnoseTaskQuery query,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM diagnose_task
            <where>
                <if test="query.appId != null and query.appId != ''">
                    AND app_id = #{query.appId}
                </if>
                <if test="query.env != null and query.env != ''">
                    AND env = #{query.env}
                </if>
                <if test="query.diagnoseType != null and query.diagnoseType != ''">
                    AND diagnose_type = #{query.diagnoseType}
                </if>
                <if test="query.status != null and query.status != ''">
                    AND status = #{query.status}
                </if>
                <if test="query.startTime != null">
                    AND created_at &gt;= #{query.startTime}
                </if>
                <if test="query.endTime != null">
                    AND created_at &lt; #{query.endTime}
                </if>
                <if test="query.keyword != null and query.keyword != ''">
                    AND (task_no LIKE CONCAT(#{query.keyword}, '%')
                         OR question LIKE CONCAT('%', #{query.keyword}, '%'))
                </if>
            </where>
            </script>
            """)
    long count(@Param("query") DiagnoseTaskQuery query);

    @Select("""
            <script>
            SELECT
                COUNT(*) AS total,
                SUM(CASE WHEN status = 'RUNNING' THEN 1 ELSE 0 END) AS running,
                SUM(CASE WHEN status = 'FINISHED' THEN 1 ELSE 0 END) AS finished,
                SUM(CASE WHEN status IN ('FAILED', 'INTERRUPTED') THEN 1 ELSE 0 END) AS failed,
                SUM(CASE WHEN status = 'CREATED' THEN 1 ELSE 0 END) AS created
            FROM diagnose_task
            <where>
                <if test="startTime != null">
                    AND created_at &gt;= #{startTime}
                </if>
            </where>
            </script>
            """)
    Map<String, Object> countByStatus(@Param("startTime") java.time.LocalDateTime startTime);

    @Select("""
            <script>
            SELECT
                IFNULL(diagnose_type, 'UNKNOWN') AS diagnoseType,
                COUNT(*) AS cnt
            FROM diagnose_task
            <where>
                <if test="startTime != null">
                    AND created_at &gt;= #{startTime}
                </if>
            </where>
            GROUP BY diagnose_type
            </script>
            """)
    List<Map<String, Object>> countByType(@Param("startTime") java.time.LocalDateTime startTime);

    @Select("""
            <script>
            SELECT
                DATE(created_at) AS day,
                COUNT(*) AS cnt,
                SUM(CASE WHEN status = 'FINISHED' THEN 1 ELSE 0 END) AS finished,
                SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed
            FROM diagnose_task
            <where>
                <if test="startTime != null">
                    AND created_at &gt;= #{startTime}
                </if>
            </where>
            GROUP BY DATE(created_at)
            ORDER BY day ASC
            </script>
            """)
    List<Map<String, Object>> dailyTrend(@Param("startTime") java.time.LocalDateTime startTime);
}

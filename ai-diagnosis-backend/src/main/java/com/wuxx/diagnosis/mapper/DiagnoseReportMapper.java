package com.wuxx.diagnosis.mapper;

import com.wuxx.diagnosis.domain.DiagnoseReport;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DiagnoseReportMapper {

    @Insert("""
            INSERT INTO diagnose_report (
                task_no,
                report_title,
                report_markdown,
                report_json,
                ai_model,
                prompt_version,
                created_at,
                updated_at
            ) VALUES (
                #{taskNo},
                #{reportTitle},
                #{reportMarkdown},
                #{reportJson},
                #{aiModel},
                #{promptVersion},
                #{createdAt},
                #{updatedAt}
            )
            """)
    int insert(DiagnoseReport report);

    @Update("""
            UPDATE diagnose_report
            SET report_title = #{reportTitle},
                report_markdown = #{reportMarkdown},
                report_json = #{reportJson},
                ai_model = #{aiModel},
                prompt_version = #{promptVersion},
                updated_at = #{updatedAt}
            WHERE task_no = #{taskNo}
            """)
    int updateByTaskNo(DiagnoseReport report);

    @Select("""
            SELECT
                id,
                task_no AS taskNo,
                report_title AS reportTitle,
                report_markdown AS reportMarkdown,
                report_json AS reportJson,
                ai_model AS aiModel,
                prompt_version AS promptVersion,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM diagnose_report
            WHERE task_no = #{taskNo}
            """)
    DiagnoseReport findByTaskNo(@Param("taskNo") String taskNo);
}

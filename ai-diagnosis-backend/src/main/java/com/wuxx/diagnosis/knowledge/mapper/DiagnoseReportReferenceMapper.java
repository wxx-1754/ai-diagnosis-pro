package com.wuxx.diagnosis.knowledge.mapper;

import java.util.List;

import com.wuxx.diagnosis.knowledge.domain.ReportKnowledgeReference;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DiagnoseReportReferenceMapper {

    @Insert("""
            INSERT INTO diagnose_report_reference (
                task_no, report_id, chunk_id, doc_no, citation_code, source_type,
                title_snapshot, source_ref_snapshot, similarity, retrieval_rank,
                usage_type, content_excerpt, created_at
            ) VALUES (
                #{taskNo}, #{reportId}, #{chunkId}, #{docNo}, #{citationCode}, #{sourceType},
                #{title}, #{sourceRef}, #{similarity}, #{retrievalRank},
                #{usageType}, #{excerpt}, #{createdAt}
            )
            """)
    int insert(ReportKnowledgeReference reference);

    @Delete("DELETE FROM diagnose_report_reference WHERE task_no = #{taskNo}")
    int deleteByTaskNo(@Param("taskNo") String taskNo);

    @Update("""
            UPDATE diagnose_report_reference
            SET usage_type = CASE
                WHEN #{reportMarkdown} LIKE CONCAT('%[', citation_code, ']%') THEN 'CITED'
                ELSE 'RETRIEVED'
            END
            WHERE task_no = #{taskNo}
            """)
    int updateUsage(@Param("taskNo") String taskNo, @Param("reportMarkdown") String reportMarkdown);

    @Update("""
            UPDATE diagnose_report_reference r
            JOIN diagnose_report d ON d.task_no = r.task_no
            SET r.report_id = d.id
            WHERE r.task_no = #{taskNo}
            """)
    int attachReportId(@Param("taskNo") String taskNo);

    @Select("""
            SELECT id, task_no, report_id, chunk_id, doc_no, citation_code,
                   source_type, title_snapshot AS title,
                   source_ref_snapshot AS sourceRef, similarity,
                   retrieval_rank AS retrievalRank, usage_type,
                   content_excerpt AS excerpt, created_at
            FROM diagnose_report_reference
            WHERE task_no = #{taskNo}
            ORDER BY retrieval_rank
            """)
    List<ReportKnowledgeReference> findByTaskNo(@Param("taskNo") String taskNo);
}

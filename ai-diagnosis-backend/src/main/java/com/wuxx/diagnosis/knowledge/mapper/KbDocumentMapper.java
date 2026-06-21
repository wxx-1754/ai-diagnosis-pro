package com.wuxx.diagnosis.knowledge.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.wuxx.diagnosis.knowledge.domain.KbDocument;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KbDocumentMapper {

    @Insert("""
            INSERT INTO kb_document (
                doc_no, title, source_type, category, diagnose_type, app_id, env,
                source_ref, content_hash, version, quality_status, status, chunk_count,
                file_size, embedding_model, embedding_dimension, uploaded_by,
                created_at, updated_at
            ) VALUES (
                #{docNo}, #{title}, #{sourceType}, #{category}, #{diagnoseType}, #{appId}, #{env},
                #{sourceRef}, #{contentHash}, #{version}, #{qualityStatus}, #{status}, #{chunkCount},
                #{fileSize}, #{embeddingModel}, #{embeddingDimension}, #{uploadedBy},
                #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KbDocument document);

    @Select("""
            SELECT *
            FROM kb_document
            WHERE doc_no = #{docNo}
              AND deleted_at IS NULL
            """)
    KbDocument findByDocNo(@Param("docNo") String docNo);

    @Select("""
            SELECT *
            FROM kb_document
            WHERE content_hash = #{contentHash}
              AND deleted_at IS NULL
            LIMIT 1
            """)
    KbDocument findByContentHash(@Param("contentHash") String contentHash);

    @Select("""
            SELECT *
            FROM kb_document
            WHERE source_ref = #{sourceRef}
              AND deleted_at IS NULL
            LIMIT 1
            """)
    KbDocument findBySourceRef(@Param("sourceRef") String sourceRef);

    @Update("""
            UPDATE kb_document
            SET version = #{version},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateVersion(@Param("id") Long id,
                      @Param("version") int version,
                      @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            <script>
            SELECT *
            FROM kb_document
            WHERE deleted_at IS NULL
            <if test="sourceType != null and sourceType != ''">
              AND source_type = #{sourceType}
            </if>
            <if test="status != null and status != ''">
              AND status = #{status}
            </if>
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<KbDocument> list(@Param("sourceType") String sourceType,
                          @Param("status") String status,
                          @Param("offset") int offset,
                          @Param("limit") int limit);

    @Update("""
            UPDATE kb_document
            SET status = #{status},
                chunk_count = #{chunkCount},
                error_message = #{errorMessage},
                indexed_at = #{indexedAt},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateIndexState(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("chunkCount") int chunkCount,
                         @Param("errorMessage") String errorMessage,
                         @Param("indexedAt") LocalDateTime indexedAt,
                         @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE kb_document
            SET status = 'DELETED',
                deleted_at = #{deletedAt},
                updated_at = #{deletedAt}
            WHERE id = #{id}
            """)
    int markDeleted(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);

    @Delete("DELETE FROM kb_document WHERE id = #{id}")
    int hardDelete(@Param("id") Long id);
}

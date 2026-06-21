package com.wuxx.diagnosis.knowledge.mapper;

import java.util.List;

import com.wuxx.diagnosis.knowledge.domain.KbChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KbChunkMapper {

    @Insert("""
            INSERT INTO kb_chunk (
                doc_id, doc_no, chunk_index, chunk_hash, content,
                vector_id, token_count, created_at
            ) VALUES (
                #{docId}, #{docNo}, #{chunkIndex}, #{chunkHash}, #{content},
                #{vectorId}, #{tokenCount}, #{createdAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(KbChunk chunk);

    @Select("SELECT * FROM kb_chunk WHERE doc_id = #{docId} ORDER BY chunk_index")
    List<KbChunk> findByDocId(@Param("docId") Long docId);

    @Select("SELECT * FROM kb_chunk WHERE vector_id = #{vectorId} LIMIT 1")
    KbChunk findByVectorId(@Param("vectorId") String vectorId);

    @Select("""
            SELECT *
            FROM kb_chunk
            WHERE MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) DESC
            LIMIT #{limit}
            """)
    List<KbChunk> fullTextSearch(@Param("query") String query, @Param("limit") int limit);

    @Delete("DELETE FROM kb_chunk WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") Long docId);
}

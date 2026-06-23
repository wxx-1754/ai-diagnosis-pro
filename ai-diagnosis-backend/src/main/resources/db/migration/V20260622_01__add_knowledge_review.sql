ALTER TABLE kb_document
    ADD COLUMN raw_content MEDIUMTEXT DEFAULT NULL AFTER content_hash,
    ADD COLUMN reviewed_content MEDIUMTEXT DEFAULT NULL AFTER raw_content,
    ADD COLUMN review_comment VARCHAR(1000) DEFAULT NULL AFTER reviewed_content,
    ADD COLUMN reviewed_by VARCHAR(64) DEFAULT NULL AFTER review_comment,
    ADD COLUMN reviewed_at DATETIME DEFAULT NULL AFTER reviewed_by,
    ADD KEY idx_kb_document_quality (quality_status, status);


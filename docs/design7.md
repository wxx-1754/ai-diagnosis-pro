# AI 诊断平台 RAG 知识库设计方案

## 1. 阶段目标

当前平台已具备「Java + SQL 运行时自主诊断 Agent」能力，但 AI 的判断完全依赖**当次任务实时采集的 Arthas / Explain 数据**与**写死在 System Prompt 里的通用经验**，存在三个短板：

```text
1. 没有记忆：历史诊断出过同样问题，下次仍从零推理，无法复用已有结论。
2. 没有领域知识：平台自身沉淀的排障 SOP、踩坑记录、组件特有调优手册无法被 Agent 使用。
3. 没有引用：报告结论不可追溯，用户无法确认"为什么这么建议"。
```

本阶段引入 **RAG（Retrieval-Augmented Generation）知识库**，目标是在不改动现有 Tool Calling 主链路的前提下，让 Agent 与报告生成环节能够：

```text
按当前诊断上下文（问题、诊断类型、appId、慢方法、SQL）
  ↓
检索相关知识片段（历史相似案例 / SOP / 调优手册）
  ↓
注入 Prompt 作为参考证据
  ↓
Agent 基于实时数据 + 知识证据生成更准、可追溯的诊断报告
```

本阶段不做：

```text
1. 不让 RAG 替代实时采集——实时 Arthas / Explain 证据仍是一手事实，知识只作参考。
2. 不做通用问答机器人——知识库仅服务于诊断场景，不开放自由聊天。
3. 不引入需要重写主链路的大模型推理框架——复用现有 Spring AI ChatClient。
```

---

## 2. 前置基础

目前系统已完成：

```text
第 1 步：appId -> Arthas 命令执行网关。
第 2 步：diagnose_task 任务管理 + arthas_command_record 命令审计。
第 3 步：固定规则诊断流程。
第 4 步：Spring AI 意图识别 + 报告生成。
第 5 步：Tool Calling 自主诊断 Agent。
第 6 步：SSE 实时输出 + 诊断事件生命周期。
第 7 步：SQL Explain + Java/SQL 联合诊断报告。
```

本阶段新增：

```text
1. 向量存储与 Embedding 能力。
2. 知识文档 ingestion（上传 / 自动沉淀）。
3. 检索服务（向量召回 + 关键词 + 元数据过滤 + 重排）。
4. 知识检索 Tool（让 Agent 主动查知识）。
5. 报告生成环节的自动证据注入与引用标注。
6. 知识库管理后端接口与前端页面。
7. 历史诊断报告自动入库（形成"平台记忆"）。
```

---

## 3. 知识库内容定位

知识库分三类来源，统一向量化但用元数据区分：

```text
A. 手工知识（SOP / 手册）
   - Arthas 各命令使用手册与典型场景
   - JVM CPU/内存/GC/线程排查 Playbook
   - SQL 执行计划解读与索引优化手册
   - 公司内部组件（自研中间件、内部 RPC）调优记录
   - 常见报错码 / 异常栈对应的处置方案

B. 历史诊断沉淀（平台记忆）
   - diagnose_report 表中已生成且经人工确认有效的诊断报告
   - 拆分为「问题现象 / 根因 / 推荐操作」结构化片段入库
   - 同类问题再次出现时直接召回，避免重复推理

C. 任务关联运行时片段（可选，二期）
   - 典型 Arthas 输出模式（如某线程池打满的 dashboard 形态）
   - 仅在确认无敏感数据时入库
```

设计原则：

```text
1. 知识 = 文本片段（chunk）+ 元数据，不是整篇文档直接召回。
2. 每个片段必须可追溯到一个 source（文档名/报告 taskNo/URL）。
3. 敏感数据入库前必须经过现有 SensitiveDataMasker / SqlSensitiveDataMasker 脱敏。
4. 历史报告入库需显式标记"已确认有效"，避免把错结论当知识。
```

---

## 4. 整体实现思路

完整检索-增强链路：

```text
诊断任务进行中
  ↓
（路径 A）Tool Calling Agent 调用 searchKnowledge 工具
  ↓     或
（路径 B）报告生成前，后端按上下文自动检索
  ↓
构建检索 Query（问题 + 诊断类型 + 慢方法 + SQL + appId）
  ↓
向量召回（Top-K1） + 关键词召回（Top-K2） + 元数据过滤
  ↓
重排（按相似度 / 诊断类型权重 / 时效性）
  ↓
取 Top-N 片段，拼装为「知识证据」
  ↓
注入 Prompt（System 或 User 段）
  ↓
Agent / 报告生成器基于 实时数据 + 知识证据 输出
  ↓
报告末尾附「参考知识来源」列表（可点击追溯到 source）
```

两条路径并存的设计原因：

```text
路径 A（Agent 主动检索）：
  Agent 在推理过程中"觉得需要参考"时调用，灵活，但依赖模型决策质量。

路径 B（报告前自动检索）：
  无论 Agent 是否调用，报告生成前强制检索一次，保证每份报告都有知识支撑与引用。
  这是兜底，避免 Agent 漏调导致 RAG 形同虚设。
```

推荐一期两者都做，路径 B 为必选，路径 A 为增强。

---

## 5. 技术选型

### 5.1 Embedding 模型

复用现有阿里云 DashScope，使用其 OpenAI 兼容的 Embedding 端点：

```text
模型：text-embedding-v3（DashScope）
维度：1024（可配置 768/1024）
调用：复用 spring-ai-starter-model-openai 的 EmbeddingModel Bean
       base-url 与 chat 共用 https://dashscope.aliyuncs.com/compatible-mode
```

理由：不引入新供应商、不新增密钥，与现有 chat 模型同一套配置体系。

### 5.2 向量存储

给出现实可选方案，按推荐度排序：

| 方案 | 说明 | 优劣 |
|------|------|------|
| **DashVector（阿里云）** | 托管向量检索服务，与 DashScope 同账号 | 免运维、与现有云一致、需 Spring AI DashVectorStore 适配；推荐生产 |
| **PgVector（新增 PG 实例）** | PostgreSQL + pgvector 扩展 | 成熟、Spring AI 原生支持、但需新增一种数据库 |
| **Redis Stack** | 复用/新增 Redis + 向量检索 | 若已有 Redis 则轻量；向量规模大时表现一般 |
| **Milvus / Qdrant 自建** | 独立向量库 | 能力强、运维重，一期不必要 |
| **SimpleVectorStore（内存）** | Spring AI 内置 | 仅用于本地 Demo / 单测，不可生产 |

**一期建议**：开发期用 `SimpleVectorStore` 跑通链路；生产部署用 **DashVector**（与现有 DashScope 同体系，零额外密钥）或 **PgVector**（若团队更熟 PG）。下文数据模型与接口以「可切换的 VectorStore 抽象」设计，不绑定具体实现。

### 5.3 框架依赖

```xml
<!-- 复用已有 -->
<!-- spring-ai-starter-model-openai（已存在，同时提供 ChatModel 与 EmbeddingModel） -->

<!-- 新增：向量存储（按选型二选一） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
</dependency>
<!-- 或 spring-ai-starter-vector-store-redis / 对应 DashVector 适配 -->

<!-- 文档解析（ingestion 时用） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pdf-document-reader</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tika-document-reader</artifactId>
</dependency>
```

> 注意 Spring AI 1.1.x 的 starter 命名，落地时以实际 BOM 提供的 artifactId 为准。

---

## 6. 数据模型

### 6.1 知识文档元数据表（业务侧）

向量库只存向量与片段，**业务元数据**（文档来源、分类、状态、操作审计）放在 MySQL，便于管理与展示：

```sql
-- V20260621_01__create_knowledge_base.sql

CREATE TABLE kb_document (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_no       VARCHAR(64)  NOT NULL UNIQUE COMMENT '文档编号',
    title        VARCHAR(255) NOT NULL COMMENT '文档标题',
    source_type  VARCHAR(32)  NOT NULL COMMENT 'MANUAL/HISTORY_REPORT/RUNTIME',
    category     VARCHAR(64)           COMMENT 'SOP/MANUAL/CASE/JVM/SQL/COMPONENT',
    diagnose_type VARCHAR(32)          COMMENT '关联诊断类型 CPU_HIGH/MEMORY/SLOW_REQUEST/... 可空',
    app_id       VARCHAR(64)           COMMENT '关联应用，可空',
    source_ref   VARCHAR(512)          COMMENT '来源引用：文件名/URL/历史 taskNo',
    content_hash VARCHAR(64)           COMMENT '原文 hash，去重用',
    chunk_count  INT          NOT NULL DEFAULT 0,
    status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/INDEXED/FAILED/DELETED',
    file_size    BIGINT                DEFAULT 0,
    uploaded_by  VARCHAR(64),
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    KEY idx_source_type (source_type),
    KEY idx_status (status),
    KEY idx_diagnose_type (diagnose_type)
) COMMENT '知识库文档元数据';

CREATE TABLE kb_chunk (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id       BIGINT       NOT NULL,
    doc_no       VARCHAR(64)  NOT NULL,
    chunk_index  INT          NOT NULL COMMENT '文档内分片序号',
    content      MEDIUMTEXT   NOT NULL COMMENT '片段原文',
    vector_id    VARCHAR(128)          COMMENT '向量库中对应 ID',
    token_count  INT,
    created_at   DATETIME     NOT NULL,
    KEY idx_doc_id (doc_id),
    KEY idx_doc_no (doc_no)
) COMMENT '知识库分片（向量库的镜像/兜底原文）';
```

说明：

```text
1. kb_chunk 同时保留原文，用于召回后直接取文本，避免反向查向量库。
2. vector_id 指向向量库中的记录，删除文档时同步删向量。
3. content_hash 用于历史报告增量入库去重，避免重复 embedding 花钱。
```

### 6.2 向量库中每个片段的元数据（metadata）

写入向量库时，每个 Document 携带如下 metadata，供检索时过滤：

```text
docNo        : 文档编号
sourceType   : MANUAL / HISTORY_REPORT / RUNTIME
category     : SOP / MANUAL / CASE / ...
diagnoseType : CPU_HIGH / SLOW_REQUEST / ...（可空，多值用逗号）
appId        : 关联应用（可空）
taskNo       : 历史报告来源任务号（可空）
title        : 文档标题
chunkIndex   : 分片序号
createdAt    : 入库时间
```

检索时可按 `diagnoseType`、`appId`、`sourceType` 过滤，例如"只召回 SLOW_REQUEST 且同 appId 的历史案例"。

### 6.3 报告与知识引用关联表

```sql
CREATE TABLE diagnose_report_reference (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_no      VARCHAR(64) NOT NULL,
    report_id    BIGINT,
    chunk_id     BIGINT,
    doc_no       VARCHAR(64) NOT NULL,
    source_type  VARCHAR(32) NOT NULL,
    similarity   DECIMAL(5,4),
    content_excerpt VARCHAR(1000) COMMENT '命中的片段摘要，便于报告展示',
    created_at   DATETIME NOT NULL,
    KEY idx_task_no (task_no),
    KEY idx_report_id (report_id)
) COMMENT '诊断报告引用的知识片段';
```

作用：报告生成时把命中的 Top-N 片段落库，前端可在报告下方展示「参考知识来源」，点击追溯到原文档或历史任务。

---

## 7. 核心模块设计

新增 `com.wuxx.diagnosis.knowledge` 包，结构对齐现有 `sql` 模块：

```text
com.wuxx.diagnosis.knowledge
├── config
│   ├── KnowledgeBaseProperties.java      # diagnosis.kb.* 配置
│   └── VectorStoreConfig.java            # VectorStore / EmbeddingModel Bean
├── ingestion
│   ├── KnowledgeIngestionService.java    # 文档解析+分片+embedding+入库
│   ├── DocumentSplitterFactory.java      # 分片策略
│   ├── HistoryReportIngestor.java        # 历史报告自动入库
│   └── SecurityMaskingTextProvider.java  # 复用现有脱敏器
├── retrieval
│   ├── KnowledgeRetrievalService.java    # 检索主入口（向量+关键词+过滤+重排）
│   ├── RetrievalQuery.java               # 检索入参 DTO
│   ├── RetrievedChunk.java               # 检索结果 DTO
│   └── HybridReranker.java               # 简单重排
├── tool
│   └── KnowledgeSearchTool.java          # @Tool，供 Agent 调用
├── controller
│   ├── KnowledgeDocumentController.java  # 文档 CRUD / 上传 / 重建索引
│   └── KnowledgeSearchController.java    # 手动检索（调试用）
├── domain
│   ├── KbDocument.java
│   ├── KbChunk.java
│   ├── KbDocumentUpsertRequest.java
│   └── KnowledgeSearchRequest.java
└── mapper
    ├── KbDocumentMapper.java
    ├── KbChunkMapper.java
    └── DiagnoseReportReferenceMapper.java
```

### 7.1 Ingestion（入库）

`KnowledgeIngestionService` 核心流程：

```text
入参：文档文件 / 文本 / 历史报告 taskNo
  ↓
脱敏（复用 SensitiveDataMasker / SqlSensitiveDataMasker）
  ↓
解析为 Spring AI Document（PDF/Markdown/TXT/Word，按扩展名选 Reader）
  ↓
分片（TokenTextSplitter，建议 chunk=500 token、overlap=80）
  ↓
写 kb_document（status=INDEXING）+ 计算 content_hash 去重
  ↓
embeddingModel.embed(分片) → 写 VectorStore（带 metadata）
  ↓
回写 kb_chunk、kb_document.status=INDEXED、chunk_count
  ↓
失败：status=FAILED，记录错误信息，不残留半成品向量
```

分片策略要点：

```text
1. 优先按语义边界切（Markdown 标题、空行），再按 token 上限二次切。
2. 每个 chunk 保留其所属文档标题作为前缀，召回后上下文更完整。
3. 历史报告按报告结构切：问题现象 / 根因 / 推荐操作 各成独立片段，
   便于精准召回"根因"片段而非整篇报告。
```

`HistoryReportIngestor`（历史沉淀）：

```text
触发：报告生成成功且任务 FINISHED 后，异步触发（不阻塞主链路）。
条件：仅入库 source_type=HISTORY_REPORT，且报告内容长度达标。
去重：同一 taskNo 不重复入库；content_hash 命中则跳过。
敏感：调用 sqlSensitiveDataMasker.maskForAi 后再入库。
可选：仅当人工标记"结论有效"才入库（一期可先用 FINISHED 即入库，二期加确认开关）。
```

### 7.2 Retrieval（检索）

`KnowledgeRetrievalService.retrieve(RetrievalQuery)`：

```text
RetrievalQuery:
  question        用户原始问题
  diagnoseType    诊断类型（强过滤优先级高）
  appId           应用
  targetClass/Method  慢方法
  sql             捕获的 SQL（SLOW_REQUEST 场景）
  topK            默认 5
  minScore        默认 0.55
```

检索逻辑：

```text
1. 构造检索文本：
   queryText = question
              + （慢方法）targetClass.targetMethod
              + （SQL）原始 SQL 去参数化后的骨架
2. 向量召回：vectorStore.similaritySearch(SearchRequest
       .query(queryText)
       .topK(topK * 2)
       .filterExpression(buildFilter(diagnoseType, appId)))
3. 关键词召回（一期可选）：对 kb_chunk.content 做 LIKE/全文索引补充召回，
   弥补向量对专有名词、错误码、类名的精确匹配弱项。
4. 合并去重（按 chunk_id）。
5. 重排 HybridReranker：
   score = 0.6 * vectorScore
         + 0.2 * diagnoseTypeMatchBonus
         + 0.1 * appIdMatchBonus
         + 0.1 * recencyBonus（历史案例越新越优先，限 sourceType=HISTORY_REPORT）
6. 取 Top-N（默认 5），低于 minScore 丢弃。
7. 落库引用（diagnose_report_reference，仅报告路径 B 调用时落）。
```

Filter 表达式示例（Spring AI FilterExpressionBuilder）：

```text
diagnoseType == 'SLOW_REQUEST' AND (appId == 'order-svc' OR appId == '__null__')
```

即：优先同应用、也接受未绑定应用的手册类知识。

### 7.3 KnowledgeSearchTool（Agent 主动检索）

新增一个 `@Tool`，注册到 `ToolCallingDiagnosisAgent.buildTools`：

```java
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final KnowledgeRetrievalService retrievalService;

    @Tool(description = "检索诊断知识库，获取与当前问题相关的历史案例、排障 SOP 或调优手册片段。"
            + "当需要参考过往相似问题处置经验、或确认某现象的常见根因时调用。"
            + "入参：taskNo、query（检索关键词或问题描述）、diagnoseType（可选）。"
            + "返回若干知识片段及其来源，仅作参考，不得替代实时采集数据。")
    public List<RetrievedChunk> searchKnowledge(String taskNo, String query, String diagnoseType) {
        // 校验 taskNo 合法性（复用 diagnoseTaskService.checkTaskAppEnv 思路）
        RetrievalQuery q = RetrievalQuery.builder()
                .question(query)
                .diagnoseType(diagnoseType)
                .topK(5)
                .build();
        return retrievalService.retrieve(q);
    }
}
```

并在 `ToolCallingDiagnosisAgent.buildTools` 中追加（与现有 SQL 工具同位置注册）：

```java
tools.add(knowledgeSearchTool);   // 所有诊断类型均可用
```

对 System Prompt 补一条规则：

```text
12. 可调用 searchKnowledge 检索历史相似案例与 SOP 作为参考。
    知识片段仅作参考，结论必须以实时采集的 Arthas / Explain 数据为准，
    不得直接照搬历史结论而忽略当前证据差异。
```

### 7.4 报告生成路径 B（自动证据注入）

改造 `DiagnosisReportGenerator.generateMarkdownReport`（及 `JavaSqlJointReportGenerator`），在构建 user prompt 前插入检索：

```text
1. 调用 knowledgeRetrievalService.retrieve(buildQuery(task, sqlRecord))
2. 把 Top-N 片段拼成「参考知识」段落：
   ----
   [来源: 历史案例 taskNo=T20260101xxx / 相似度 0.82]
   根因：xxx
   推荐：xxx
   ----
3. 拼入 user prompt 的 Arthas 输出之后、报告结构要求之前。
4. System Prompt 增加：报告"推荐操作"可参考但不得照抄知识库；
   "风险提示"需说明建议是否参考了历史案例。
5. 同步落 diagnose_report_reference，供前端展示来源。
```

> 路径 B 是兜底：即使 Agent 一次都没调 searchKnowledge，报告也带知识证据与引用。

### 7.5 与现有 SSE / 事件流集成

新增两个事件类型（追加到 `DiagnoseEventType`）：

```text
KNOWLEDGE_RETRIEVING   "正在检索诊断知识库"
KNOWLEDGE_RETRIEVED    "已检索到 N 条相关知识"  data: {count, sources}
```

在路径 B 检索前后通过 `DiagnoseSseManager` 推送，前端 event-detail 可显示"正在参考历史案例…"，提升可解释性与体验。

---

## 8. 对现有代码的改动点（清单）

| 文件 | 改动 |
|------|------|
| `pom.xml` | 新增 vector-store starter、document-reader 依赖 |
| `application.yml` | 新增 `diagnosis.kb.*` 配置块 + 向量库连接配置 |
| `AiConfig.java` | 暴露 `EmbeddingModel`（若 starter 未自动配置则补 Bean） |
| `DiagnosisAiProperties.java` | 新增 `KnowledgeBaseProperties`（独立 properties 类更清晰） |
| `ToolCallingDiagnosisAgent.java` | `buildTools` 注册 `KnowledgeSearchTool`；System Prompt 增补规则 |
| `DiagnosisReportGenerator.java` | 生成前检索并注入证据 + 落引用 |
| `JavaSqlJointReportGenerator.java` | 同上（联合报告路径） |
| `AgentDiagnoseAsyncService.java` | 报告生成后异步触发 `HistoryReportIngestor`；推送新 SSE 事件 |
| `DiagnoseEventType.java` | 新增两个事件枚举 |
| 新增 `knowledge/**` 整包 | 见第 7 节 |
| 新增 Flyway 迁移 `V20260621_01__create_knowledge_base.sql` | 见第 6 节 |

改动遵循现有约束：

```text
1. 新模块用 @ConditionalOnProperty(prefix="diagnosis.kb", name="enable", havingValue="true") 守卫，
   关闭时不影响主链路（与现有 diagnosis.ai / diagnosis.sql 一致）。
2. 所有 @Service / @Component 复用构造器注入 + Lombok @RequiredArgsConstructor。
3. Mapper 走 MyBatis，map-underscore-to-camel-case 已开。
4. 不引入新密钥：Embedding 复用 AI_API_KEY / AI_BASE_URL。
```

---

## 9. 接口设计

### 9.1 知识库管理接口（管理员）

```text
POST   /api/kb/documents/upload      上传文档（multipart），自动解析入库
POST   /api/kb/documents/text        直接提交文本入库（用于粘贴 SOP）
GET    /api/kb/documents             文档列表（分页、按 sourceType/category 过滤）
GET    /api/kb/documents/{docNo}     文档详情 + 分片预览
DELETE /api/kb/documents/{docNo}     删除文档（同步删向量 + kb_chunk）
POST   /api/kb/documents/{docNo}/reindex  重建索引
POST   /api/kb/history/sync          手动触发历史报告批量入库（指定 taskNo 列表或时间范围）
```

鉴权：复用现有 `SqlAdminAccessGuard` 思路，新增 `KbAdminAccessGuard`，管理员操作走 admin-token。

### 9.2 检索接口（调试 / 前端预览）

```text
POST   /api/kb/search
Body:  { "question":"...", "diagnoseType":"SLOW_REQUEST", "appId":"order-svc", "topK":5 }
Resp:  [ { "docNo","title","sourceType","similarity","content","sourceRef" } ]
```

### 9.3 报告内引用（前端消费）

报告 payload（`DiagnosisReportPayload`）新增字段：

```text
references: [
  { docNo, title, sourceType, sourceRef, similarity, excerpt }
]
```

前端 event-detail.js 在报告下方渲染「参考知识来源」列表，`sourceRef` 为历史 taskNo 时可跳转到该历史任务详情。

---

## 10. 配置项设计

`application.yml` 新增：

```yaml
diagnosis:
  kb:
    enable: ${DIAGNOSIS_KB_ENABLE:false}          # 默认关闭，灰度开启
    embedding-model: ${AI_EMBEDDING_MODEL:text-embedding-v3}
    embedding-dim: ${DIAGNOSIS_KB_EMBEDDING_DIM:1024}
    chunk-size: ${DIAGNOSIS_KB_CHUNK_SIZE:500}
    chunk-overlap: ${DIAGNOSIS_KB_CHUNK_OVERLAP:80}
    retrieve-top-k: ${DIAGNOSIS_KB_TOP_K:5}
    min-score: ${DIAGNOSIS_KB_MIN_SCORE:0.55}
    auto-ingest-history: ${DIAGNOSIS_KB_AUTO_INGEST_HISTORY:true}
    history-min-content-length: ${DIAGNOSIS_KB_HISTORY_MIN_LEN:200}
    vector-store: ${DIAGNOSIS_KB_VECTOR_STORE:simple}   # simple/pgvector/redis/dashvector
    # 各向量库连接参数按选型补充，与 spring.ai.vectorstore.* 对齐
```

默认 `enable=false`，保证上线初期不影响生产主链路；灰度验证后再开。

---

## 11. 落地步骤（建议分期）

### 一期：链路打通（用 SimpleVectorStore）

```text
1. 加依赖、加配置、加 Flyway 建表。
2. 实现 KnowledgeIngestionService（先支持 Markdown/TXT）。
3. 实现 KnowledgeRetrievalService（纯向量召回）。
4. 实现 KnowledgeSearchTool，注册到 Agent。
5. 改造 DiagnosisReportGenerator 路径 B（注入证据 + 落引用）。
6. 新增管理接口 + 手动检索接口。
7. 单测：ingestion 去重、检索过滤、报告注入。
8. 用 SimpleVectorStore 跑通端到端，验证效果。
```

### 二期：历史沉淀 + 生产向量库

```text
1. HistoryReportIngestor 接入 AgentDiagnoseAsyncService 报告完成钩子。
2. 切换到 DashVector / PgVector。
3. 关键词召回 + HybridReranker 补齐。
4. 前端知识库管理页 + 报告引用展示。
5. 灰度开启 auto-ingest-history，观察知识质量。
```

### 三期：质量治理

```text
1. 报告引用点击率 / 命中率埋点。
2. 历史报告"人工确认有效"标记，过滤错结论。
3. 知识时效性：旧版本组件手册自动失效标记。
4. 检索 query 改写（用小模型把口语化问题改写为检索语义）。
```

---

## 12. 风险与对策

| 风险 | 对策 |
|------|------|
| 历史报告含错结论被当知识复用 | 一期 FINISHED 即入库但标注"未确认"；二期加人工确认门禁；报告引用必须展示来源供人判断 |
| 入库敏感数据泄露 | 入库前强制走现有脱敏器；SQL 片段用 maskForAi；runtime 类知识默认关闭 |
| 向量召回对错误码/类名精确匹配弱 | 补关键词召回（kb_chunk 全文/LIKE）；重排时给精确匹配加分 |
| Embedding 调用成本与延迟 | 历史报告增量去重（content_hash）；检索 topK 适中；embedding 可异步批处理 |
| 知识与实时证据冲突导致 Agent 照抄历史 | System Prompt 强约束"以实时数据为准"；报告"根因分析"必须引用当次 Arthas/Explain 证据 |
| 向量库引入新运维负担 | 一期 SimpleVectorStore 验证逻辑；生产选托管型 DashVector；enable=false 默认关闭可回退 |
| 检索噪声拉低报告质量 | min-score 门槛 + 重排 + Top-N 限制；证据不足时不注入而非硬塞 |

---

## 13. 验收标准

```text
1. 上传一份 Markdown SOP，可在管理接口查到 INDEXED，分片数正确。
2. 发起一个 SLOW_REQUEST 诊断任务，报告"参考知识来源"非空且可追溯到 source。
3. 同类问题第二次诊断，Agent 能通过 searchKnowledge 或自动注入命中历史案例片段。
4. diagnosis.kb.enable=false 时，主链路与改造前行为完全一致（无回归）。
5. 删除文档后，向量库与 kb_chunk 同步清除，检索不再命中。
6. 历史报告入库经脱敏，grep 不到明文密码/敏感参数。
```

---

## 14. 与现有架构的关系图

```text
                 ┌───────────────────────┐
   前端 ───────► │ AgentDiagnoseController│
                 └──────────┬────────────┘
                            │ SSE
                            ▼
                ┌────────────────────────┐
                │ AgentDiagnoseAsyncService│
                └──────────┬─────────────┘
                           │
        ┌──────────────────┼─────────────────────┐
        ▼                  ▼                     ▼
 ┌─────────────┐  ┌──────────────────┐  ┌────────────────────┐
 │IntentClassif│  │HybridDiagnosisExec│  │报告完成后异步        │
 └─────────────┘  │  └ ToolCallingAgent│  │HistoryReportIngestor│
                  │     ├ ArthasTools  │  └─────────┬──────────┘
                  │     ├ SqlTools     │            │ 脱敏+分片+embed
                  │     └ KnowledgeSearchTool ◄──────┐
                  └──────────┬───────────┘            │
                             │                        ▼
                  ┌──────────▼───────────┐   ┌─────────────────┐
                  │DiagnosisReportGen    │──►│ VectorStore     │
                  │  (路径B: 检索+注入+引用)│   │ (DashVector/PG) │
                  └──────────┬───────────┘   └────────┬────────┘
                             │                        │
                             ▼                        │
                    ┌────────────────┐     检索 ◄──────┘
                    │ diagnose_report │
                    │ + references    │
                    └────────────────┘
```

---

## 15. 小结

本方案以最小侵入方式为现有「Java + SQL 诊断 Agent」补上**记忆**与**可追溯引用**：

```text
1. 复用 Spring AI 的 EmbeddingModel / VectorStore 抽象，不引入新推理框架。
2. 复用 DashScope 同一套密钥与端点，不新增供应商。
3. 复用现有脱敏器、任务校验、SSE 事件、Flyway 迁移、admin 鉴权体系。
4. 通过 KnowledgeSearchTool（路径 A）+ 报告前自动注入（路径 B）双保险落地 RAG。
5. 历史报告自动沉淀形成"平台记忆"，同类问题越用越准。
6. diagnosis.kb.enable 默认关闭，灰度可控，不影响现有主链路。
```

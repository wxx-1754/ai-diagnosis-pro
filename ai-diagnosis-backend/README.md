# AI Diagnosis Backend

第一阶段后端提供一个受控的 Arthas HTTP 执行网关：

```text
appId + env + commandType -> 固定 Arthas 命令 -> Arthas HTTP API -> 审计记录
```

前端或后续 AI Agent 不能直接传 Arthas 原生命令，只能传 `commandType`。

## 支持的 commandType

| commandType | Arthas command |
| --- | --- |
| `dashboard` | `dashboard -n 1` |
| `thread` | `thread` |
| `topThread` | `thread -n 5` |
| `jvm` | `jvm` |
| `memory` | `memory` |

## 配置

所有敏感配置（AI API Key、数据库密码等）都通过环境变量注入，`application.yml` 里只有占位默认值，**切勿把真实密钥写进代码或配置默认值**。

### 本地开发

仓库根目录提供了 `.env.example` 模板：

```bash
cp .env.example .env      # 填入真实密钥（.env 已被 .gitignore 忽略）
```

启动前加载环境变量：

```bash
# bash
set -a && source .env && set +a && mvn spring-boot:run

# PowerShell
$env:AI_API_KEY="..."; $env:MYSQL_PASSWORD="..."; mvn spring-boot:run
```

也可在 IDE 的运行配置里直接填环境变量。

### 关键环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `AI_API_KEY` | AI 模型 API Key（必填） | 空 |
| `AI_BASE_URL` | 兼容 OpenAI 协议的 base url | `https://dashscope.aliyuncs.com/compatible-mode` |
| `AI_CHAT_MODEL` | 模型名 | `qwen-plus` |
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | MySQL 连接 | `localhost` / `3306` / `ai_diagnosis` |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 账号 | `root` / 空 |

完整变量见 `.env.example`。

Arthas 网关参数在 `src/main/resources/application.yml`：

```yaml
diagnosis:
  arthas:
    connect-timeout-ms: 3000
    read-timeout-ms: 10000
    max-output-length: 20000
    audit-output-excerpt-length: 4000
```

## 启动

项目现已接入 Flyway。启动时会自动执行 `src/main/resources/db/migration` 下尚未执行的迁移：

```text
V20260620_01__create_diagnose_event.sql
V20260620_02__create_sql_diagnosis.sql
V20260621_01__create_knowledge_base.sql
```

已有非空数据库第一次启用 Flyway 时会以 `20260620.2` 为基线，只执行知识库及后续迁移。

```bash
mvn spring-boot:run
```

服务默认监听：

```text
http://localhost:9001
```

## 日志

日志会同时输出到控制台和文件：

```text
logs/ai-diagnosis-backend.log
```

可通过环境变量临时调整日志级别或目录：

```bash
export LOG_LEVEL_ROOT=INFO
export LOG_LEVEL_APP=DEBUG
export LOG_PATH=logs
```

## 调用示例

执行 JVM 诊断：

```bash
curl -X POST http://localhost:9001/api/arthas/execute \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "order-service",
    "env": "test",
    "commandType": "jvm"
  }'
```

健康检查使用固定 `jvm` 命令：

```bash
curl "http://localhost:9001/api/arthas/health?appId=order-service&env=test"
```

前端服务和环境选择器从在线实例表读取：

```bash
curl "http://localhost:9001/api/app-instances/options"
```

接口仅返回 `appId`、`appName` 和 `env`，不会暴露 Arthas 地址或认证信息。

如果目标 Arthas HTTP API 不可连接，接口会返回 `success=false`，并保存 `arthas_command_record` 审计记录。

## 测试

```bash
mvn test
```

## SQL Explain 联合诊断

当前仅支持 MySQL。已完成的 `SLOW_REQUEST` 任务可以继续提交只读 SQL：

```http
POST /api/joint-diagnose/java-sql
Content-Type: application/json

{
  "taskNo": "DIAG-...",
  "datasourceCode": "order_db",
  "mainTableName": "t_order",
  "sql": "select * from t_order where user_id = 1001"
}
```

SQL 必须是单条、已替换占位符的 `SELECT` 或 `WITH SELECT`。后端执行
`EXPLAIN FORMAT=JSON` 和受控的 `information_schema` 查询，不会执行用户 SQL 本身。

数据源管理接口默认关闭。需要管理数据源时设置：

```text
DIAGNOSIS_SQL_ADMIN_ENABLED=true
DIAGNOSIS_SQL_ADMIN_TOKEN=<管理令牌>
DIAGNOSIS_SQL_ENCRYPTION_KEY=<至少 32 字符的随机密钥>
```

并在 `/api/admin/sql/datasources` 请求中携带：

```http
X-Diagnosis-Admin-Token: <管理令牌>
```

管理接口不会返回数据库密码，更新数据源时不传 `password` 表示保留原密码。

### 开发/测试环境自动捕获 SQL

如果希望 Tool Calling Agent 自主生成 `watch` 等原生 Arthas 命令，并在捕获 SQL 后自动执行 Explain，需要显式开启：

```text
DIAGNOSIS_ARTHAS_UNRESTRICTED_AI_COMMANDS_ENABLED=true
DIAGNOSIS_ARTHAS_UNRESTRICTED_AI_ENVIRONMENTS=dev,test
```

该功能仅允许配置列表中的任务环境。即使开关开启，`prod`、`uat`、`staging`
等未列入的环境仍会被后端拒绝。

慢请求诊断时应使用 `TOOL_CALLING` 模式，并在 `trace/watch` 采样窗口内实际请求目标接口。
Agent 会尝试：

```text
trace Controller
→ watch MyBatis/JDBC 获取实际 SQL 和参数
→ 选择当前环境已配置的数据源
→ EXPLAIN FORMAT=JSON
→ 生成 Java + SQL 联合诊断报告
```

原生 Arthas 命令具备修改甚至终止目标 JVM 的能力，只应对可随时重启、无真实数据的开发测试实例开启。

## RAG 知识库一期

一期支持 Markdown、纯文本上传，报告生成前自动检索，并将命中的知识引用独立落库。
默认关闭，启用方式：

```text
DIAGNOSIS_KB_ENABLED=true
DIAGNOSIS_KB_ADMIN_ENABLED=true
DIAGNOSIS_KB_ADMIN_TOKEN=<管理令牌>
AI_EMBEDDING_MODEL=text-embedding-v3
DIAGNOSIS_KB_EMBEDDING_DIM=1024
```

管理接口统一携带：

```http
X-Diagnosis-Admin-Token: <管理令牌>
```

主要接口：

```text
POST   /api/admin/kb/documents/text
POST   /api/admin/kb/documents/upload
GET    /api/admin/kb/documents
GET    /api/admin/kb/documents/{docNo}
GET    /api/admin/kb/documents/{docNo}/chunks
POST   /api/admin/kb/documents/{docNo}/reindex   # 按当前分片参数重建索引
DELETE /api/admin/kb/documents/{docNo}
POST   /api/admin/kb/search
GET    /api/ai/diagnose/{taskNo}/report/references
```

前端在侧栏「知识库」入口提供管理页：填写管理令牌后可上传文本/文件、检索预览、
查看文档与分片、重建索引、删除文档。令牌保存在浏览器 localStorage。

### 历史报告自动沉淀

`DIAGNOSIS_KB_AUTO_INGEST_HISTORY=true`（默认开启，仅当 KB 总开关开启时生效）时，
每次 Agent 诊断报告落库后会异步把报告 Markdown 作为 `source_type=HISTORY_REPORT`
的知识入库，形成平台记忆，同类问题再次出现可被检索复用。按 `taskNo`（source_ref）
去重，同一任务不重复入库；入库前同样经过脱敏。`DIAGNOSIS_KB_HISTORY_MIN_CONTENT_LENGTH`
控制报告最短长度阈值，过短的不入库。

当前向量存储为 `SimpleVectorStore`，索引文件默认保存在
`data/kb-simple-vector-store.json`，只适合单实例验证。生产环境应在二期切换为 PgVector
或经过验证的托管向量服务。

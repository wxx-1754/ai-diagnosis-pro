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

首次升级到支持“接续观察”的版本前，需要手动执行版本化 SQL：

```text
src/main/resources/db/migration/V20260620_01__create_diagnose_event.sql
```

该脚本新增持久化诊断事件表。本项目暂未接入 Flyway，应用不会自动执行此脚本。

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

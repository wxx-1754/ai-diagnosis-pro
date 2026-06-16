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

默认读取本机 MySQL：

```text
jdbc:mysql://localhost:3306/ai_diagnosis
username=root
password=<empty>
```

可通过环境变量覆盖：

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DATABASE=ai_diagnosis
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=your_password
```

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

如果目标 Arthas HTTP API 不可连接，接口会返回 `success=false`，并保存 `arthas_command_record` 审计记录。

## 测试

```bash
mvn test
```

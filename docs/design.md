# AI + Arthas 诊断 Agent 第一阶段落地方案

## 1. 阶段目标

第一阶段目标是先打通后端服务与 Arthas 的执行链路，使诊断平台可以根据 `appId` 找到目标 Java 应用，并执行受控 Arthas 命令。

本阶段支持以下命令：

```text
dashboard -n 1
thread
thread -n 5
jvm
memory
```

本阶段不开放以下高风险命令：

```text
trace
watch
ognl
heapdump
redefine
retransform
logger 修改
dump
jad
stop
shutdown
```

本阶段最终形成一个安全的：

```text
appId -> Arthas 命令执行网关
```

后续 AI Agent 只调用该网关，不直接拼接或执行任意 Arthas 命令。

---

## 2. 整体实现思路

完整调用链如下：

```text
前端 / curl
  ↓
POST /api/arthas/execute
  ↓
ArthasCommandController
  ↓
ArthasCommandService
  ↓
AppInstanceService 查询 appId 对应 ip + port
  ↓
ArthasCommandFactory 根据 commandType 生成固定命令
  ↓
ArthasCommandGuard 校验命令白名单
  ↓
ArthasHttpCommandExecutor 调用 Arthas HTTP API
  ↓
保存 arthas_command_record 审计记录
  ↓
返回命令执行结果
```

核心原则：

```text
1. 前端不能直接传 Arthas command。
2. 前端只能传 commandType。
3. 后端根据 commandType 映射成固定 Arthas 命令。
4. 所有命令执行前必须经过白名单校验。
5. 每次命令执行都必须落库审计。
```

---

## 3. 接入方式选择

### 3.1 方式一：直接调用目标应用 Arthas HTTP API

适合 MVP、本地环境、测试环境、少量应用实例。

```text
诊断平台
  ↓ HTTP
目标应用 Arthas HTTP API
```

示例：

```text
order-service: 127.0.0.1:8563
user-service: 127.0.0.1:8564
settlement-service: 127.0.0.1:8565
```

后端维护映射：

```text
appId + env -> ip + arthasHttpPort
```

第一阶段推荐使用这种方式，简单直接。

---

### 3.2 方式二：通过 Arthas Tunnel Server

适合后续生产化、多应用、多实例场景。

```text
诊断平台
  ↓
Arthas Tunnel Server
  ↓
目标应用 Arthas Agent
```

后端维护映射：

```text
appId + env -> arthasAgentId
```

第一阶段可以先预留字段，不必马上实现。

---

## 4. 技术选型

| 模块         | 选型                           |
| ------------ | ------------------------------ |
| JDK          | JDK 21                         |
| 后端框架     | Spring Boot 3.x                |
| HTTP 客户端  | Spring RestClient              |
| ORM          | MyBatis                        |
| 数据库       | MySQL 8                        |
| 参数校验     | spring-boot-starter-validation |
| 诊断工具     | Arthas HTTP API                |
| 日志         | Logback                        |
| 后续 AI 接入 | Spring AI / LangChain4j        |

---

## 5. Maven 依赖

```xml
<dependencies>
    <!-- Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- MyBatis -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>3.0.3</version>
    </dependency>

    <!-- MySQL -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Lombok，可选 -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Jackson -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

---

## 6. application.yml

```yaml
server:
  port: 9001

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_diagnosis?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true

diagnosis:
  arthas:
    connect-timeout-ms: 3000
    read-timeout-ms: 10000
    max-output-length: 20000
```

---

## 7. 数据库表设计

### 7.1 应用实例表

用于维护 `appId + env -> Arthas 地址` 的映射。

```sql
CREATE TABLE app_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) NOT NULL COMMENT '应用ID，如 order-service',
    app_name VARCHAR(128) NOT NULL COMMENT '应用名称',
    env VARCHAR(32) NOT NULL COMMENT '环境：dev/test/prod',
    ip VARCHAR(64) NOT NULL COMMENT '目标应用IP',
    arthas_http_port INT NOT NULL COMMENT 'Arthas HTTP端口，默认8563',
    arthas_username VARCHAR(64) DEFAULT NULL COMMENT 'Arthas用户名，可选',
    arthas_password VARCHAR(128) DEFAULT NULL COMMENT 'Arthas密码，可选',
    arthas_agent_id VARCHAR(128) DEFAULT NULL COMMENT 'Arthas Tunnel Agent ID，后续预留',
    access_mode VARCHAR(32) DEFAULT 'HTTP' COMMENT 'HTTP/TUNNEL',
    status VARCHAR(32) NOT NULL DEFAULT 'ONLINE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_app_env (app_id, env)
);
```

示例数据：

```sql
INSERT INTO app_instance (
    app_id,
    app_name,
    env,
    ip,
    arthas_http_port,
    access_mode,
    status,
    created_at,
    updated_at
) VALUES (
    'order-service',
    '订单服务',
    'test',
    '127.0.0.1',
    8563,
    'HTTP',
    'ONLINE',
    NOW(),
    NOW()
);
```

---

### 7.2 Arthas 命令调用记录表

```sql
CREATE TABLE arthas_command_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_no VARCHAR(64) NOT NULL COMMENT '请求编号',
    app_id VARCHAR(64) NOT NULL,
    env VARCHAR(32) NOT NULL,
    command VARCHAR(512) NOT NULL,
    command_type VARCHAR(64) NOT NULL COMMENT 'DASHBOARD/THREAD/TOP_THREAD/JVM/MEMORY',
    success TINYINT NOT NULL,
    cost_millis BIGINT NOT NULL,
    output_excerpt TEXT,
    error_message TEXT,
    created_at DATETIME NOT NULL,
    KEY idx_app_env_time (app_id, env, created_at),
    KEY idx_request_no (request_no)
);
```

---

## 8. 项目结构

```text
com.example.diagnosis
├── controller
│   └── ArthasCommandController.java
├── service
│   ├── ArthasCommandService.java
│   └── AppInstanceService.java
├── arthas
│   ├── ArthasCommandExecutor.java
│   ├── ArthasHttpCommandExecutor.java
│   ├── ArthasCommandGuard.java
│   ├── ArthasCommandType.java
│   └── ArthasCommandFactory.java
├── domain
│   ├── AppInstance.java
│   ├── ArthasCommandRecord.java
│   ├── ArthasExecuteRequest.java
│   ├── ArthasExecuteResponse.java
│   └── ArthasApiResponse.java
├── mapper
│   ├── AppInstanceMapper.java
│   └── ArthasCommandRecordMapper.java
└── config
    ├── HttpClientConfig.java
    └── GlobalExceptionHandler.java
```

---

## 9. 目标应用如何启动 Arthas

### 9.1 启动业务应用

```bash
java -jar order-service.jar
```

### 9.2 启动 Arthas

```bash
java -jar arthas-boot.jar
```

选择目标 Java 进程后，Arthas 会启动对应的 telnet/http 服务。  
默认 Web Console 常见端口为 `8563`。

### 9.3 测试 Arthas HTTP API

```bash
curl -X POST http://127.0.0.1:8563/api \
  -H "Content-Type: application/json" \
  -d '{
    "action": "exec",
    "command": "jvm"
  }'
```

如果能返回 JVM 信息，说明 Arthas HTTP 链路可用。

---

## 10. 核心代码实现

### 10.1 请求对象

```java
package com.example.diagnosis.domain;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArthasExecuteRequest {

    @NotBlank(message = "appId不能为空")
    private String appId;

    @NotBlank(message = "env不能为空")
    private String env;

    /**
     * dashboard / thread / topThread / jvm / memory
     */
    @NotBlank(message = "commandType不能为空")
    private String commandType;
}
```

---

### 10.2 响应对象

```java
package com.example.diagnosis.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArthasExecuteResponse {

    private String requestNo;

    private String appId;

    private String env;

    private String command;

    private boolean success;

    private String output;

    private String errorMessage;

    private long costMillis;
}
```

---

### 10.3 应用实例实体

```java
package com.example.diagnosis.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppInstance {

    private Long id;

    private String appId;

    private String appName;

    private String env;

    private String ip;

    private Integer arthasHttpPort;

    private String arthasUsername;

    private String arthasPassword;

    private String arthasAgentId;

    private String accessMode;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

---

### 10.4 Arthas API 响应对象

```java
package com.example.diagnosis.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ArthasApiResponse {

    private String sessionId;

    private String consumerId;

    private String state;

    private String body;

    private JsonNode results;

    private String message;
}
```

---

### 10.5 命令调用记录实体

```java
package com.example.diagnosis.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArthasCommandRecord {

    private Long id;

    private String requestNo;

    private String appId;

    private String env;

    private String command;

    private String commandType;

    private Boolean success;

    private Long costMillis;

    private String outputExcerpt;

    private String errorMessage;

    private LocalDateTime createdAt;
}
```

---

## 11. 命令类型枚举

```java
package com.example.diagnosis.arthas;

public enum ArthasCommandType {

    DASHBOARD("dashboard", "dashboard -n 1"),
    THREAD("thread", "thread"),
    TOP_THREAD("topThread", "thread -n 5"),
    JVM("jvm", "jvm"),
    MEMORY("memory", "memory");

    private final String code;
    private final String command;

    ArthasCommandType(String code, String command) {
        this.code = code;
        this.command = command;
    }

    public String getCode() {
        return code;
    }

    public String getCommand() {
        return command;
    }

    public static ArthasCommandType fromCode(String code) {
        for (ArthasCommandType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported commandType: " + code);
    }
}
```

---

## 12. 命令工厂

```java
package com.example.diagnosis.arthas;

import org.springframework.stereotype.Component;

@Component
public class ArthasCommandFactory {

    public String buildCommand(String commandType) {
        ArthasCommandType type = ArthasCommandType.fromCode(commandType);
        return type.getCommand();
    }
}
```

---

## 13. 命令安全校验

```java
package com.example.diagnosis.arthas;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ArthasCommandGuard {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "dashboard",
            "thread",
            "jvm",
            "memory"
    );

    private static final Set<String> FORBIDDEN_COMMANDS = Set.of(
            "heapdump",
            "ognl",
            "dump",
            "jad",
            "redefine",
            "retransform",
            "stop",
            "shutdown",
            "logger",
            "watch",
            "trace"
    );

    public void check(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Arthas command cannot be empty");
        }

        String normalized = command.trim();

        if (containsShellMeta(normalized)) {
            throw new SecurityException("Command injection risk detected: " + normalized);
        }

        String prefix = normalized.split("\\s+")[0];

        if (FORBIDDEN_COMMANDS.contains(prefix)) {
            throw new SecurityException("Forbidden Arthas command: " + prefix);
        }

        if (!ALLOWED_COMMANDS.contains(prefix)) {
            throw new SecurityException("Unsupported Arthas command: " + prefix);
        }

        checkCommandDetail(normalized);
    }

    private void checkCommandDetail(String command) {
        if (command.startsWith("dashboard")) {
            if (!"dashboard -n 1".equals(command)) {
                throw new SecurityException("Only dashboard -n 1 is allowed");
            }
        }

        if (command.startsWith("thread -n")) {
            if (!"thread -n 5".equals(command)) {
                throw new SecurityException("Only thread -n 5 is allowed");
            }
        }
    }

    private boolean containsShellMeta(String command) {
        return command.contains(";")
                || command.contains("&&")
                || command.contains("||")
                || command.contains("|")
                || command.contains("`")
                || command.contains("$(")
                || command.contains(">");
    }
}
```

---

## 14. Mapper 实现

### 14.1 AppInstanceMapper

```java
package com.example.diagnosis.mapper;

import com.example.diagnosis.domain.AppInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppInstanceMapper {

    @Select("""
            SELECT
                id,
                app_id AS appId,
                app_name AS appName,
                env,
                ip,
                arthas_http_port AS arthasHttpPort,
                arthas_username AS arthasUsername,
                arthas_password AS arthasPassword,
                arthas_agent_id AS arthasAgentId,
                access_mode AS accessMode,
                status,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM app_instance
            WHERE app_id = #{appId}
              AND env = #{env}
              AND status = 'ONLINE'
            LIMIT 1
            """)
    AppInstance findOnlineByAppIdAndEnv(@Param("appId") String appId,
                                        @Param("env") String env);
}
```

---

### 14.2 ArthasCommandRecordMapper

```java
package com.example.diagnosis.mapper;

import com.example.diagnosis.domain.ArthasCommandRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArthasCommandRecordMapper {

    @Insert("""
            INSERT INTO arthas_command_record (
                request_no,
                app_id,
                env,
                command,
                command_type,
                success,
                cost_millis,
                output_excerpt,
                error_message,
                created_at
            ) VALUES (
                #{requestNo},
                #{appId},
                #{env},
                #{command},
                #{commandType},
                #{success},
                #{costMillis},
                #{outputExcerpt},
                #{errorMessage},
                #{createdAt}
            )
            """)
    int insert(ArthasCommandRecord record);
}
```

---

## 15. AppInstanceService

```java
package com.example.diagnosis.service;

import com.example.diagnosis.domain.AppInstance;
import com.example.diagnosis.mapper.AppInstanceMapper;
import org.springframework.stereotype.Service;

@Service
public class AppInstanceService {

    private final AppInstanceMapper appInstanceMapper;

    public AppInstanceService(AppInstanceMapper appInstanceMapper) {
        this.appInstanceMapper = appInstanceMapper;
    }

    public AppInstance getOnlineInstance(String appId, String env) {
        AppInstance instance = appInstanceMapper.findOnlineByAppIdAndEnv(appId, env);
        if (instance == null) {
            throw new IllegalArgumentException("No online app instance found, appId="
                    + appId + ", env=" + env);
        }
        return instance;
    }
}
```

---

## 16. Arthas 执行器接口

```java
package com.example.diagnosis.arthas;

import com.example.diagnosis.domain.AppInstance;
import com.example.diagnosis.domain.ArthasExecuteResponse;

public interface ArthasCommandExecutor {

    ArthasExecuteResponse execute(AppInstance instance,
                                  String requestNo,
                                  String command,
                                  String commandType);
}
```

---

## 17. HTTP 客户端配置

```java
package com.example.diagnosis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }
}
```

---

## 18. Arthas HTTP 执行器

```java
package com.example.diagnosis.arthas;

import com.example.diagnosis.domain.AppInstance;
import com.example.diagnosis.domain.ArthasApiResponse;
import com.example.diagnosis.domain.ArthasExecuteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
public class ArthasHttpCommandExecutor implements ArthasCommandExecutor {

    private final RestClient restClient;
    private final ArthasCommandGuard commandGuard;

    public ArthasHttpCommandExecutor(RestClient restClient,
                                     ArthasCommandGuard commandGuard) {
        this.restClient = restClient;
        this.commandGuard = commandGuard;
    }

    @Override
    public ArthasExecuteResponse execute(AppInstance instance,
                                         String requestNo,
                                         String command,
                                         String commandType) {
        long start = System.currentTimeMillis();

        try {
            commandGuard.check(command);

            String url = buildApiUrl(instance);

            Map<String, Object> body = new HashMap<>();
            body.put("action", "exec");
            body.put("command", command);

            ArthasApiResponse apiResponse = restClient.post()
                    .uri(url)
                    .body(body)
                    .retrieve()
                    .body(ArthasApiResponse.class);

            String output = extractOutput(apiResponse);
            long cost = System.currentTimeMillis() - start;

            return ArthasExecuteResponse.builder()
                    .requestNo(requestNo)
                    .appId(instance.getAppId())
                    .env(instance.getEnv())
                    .command(command)
                    .success(true)
                    .output(output)
                    .costMillis(cost)
                    .build();

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;

            return ArthasExecuteResponse.builder()
                    .requestNo(requestNo)
                    .appId(instance.getAppId())
                    .env(instance.getEnv())
                    .command(command)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .costMillis(cost)
                    .build();
        }
    }

    private String buildApiUrl(AppInstance instance) {
        return "http://" + instance.getIp() + ":" + instance.getArthasHttpPort() + "/api";
    }

    private String extractOutput(ArthasApiResponse response) {
        if (response == null) {
            return "";
        }

        if (response.getBody() != null) {
            return response.getBody();
        }

        JsonNode results = response.getResults();
        if (results != null) {
            return results.toPrettyString();
        }

        if (response.getMessage() != null) {
            return response.getMessage();
        }

        return "";
    }
}
```

---

## 19. ArthasCommandService

```java
package com.example.diagnosis.service;

import com.example.diagnosis.arthas.ArthasCommandExecutor;
import com.example.diagnosis.arthas.ArthasCommandFactory;
import com.example.diagnosis.domain.AppInstance;
import com.example.diagnosis.domain.ArthasCommandRecord;
import com.example.diagnosis.domain.ArthasExecuteRequest;
import com.example.diagnosis.domain.ArthasExecuteResponse;
import com.example.diagnosis.mapper.ArthasCommandRecordMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ArthasCommandService {

    private final AppInstanceService appInstanceService;
    private final ArthasCommandFactory commandFactory;
    private final ArthasCommandExecutor commandExecutor;
    private final ArthasCommandRecordMapper commandRecordMapper;

    public ArthasCommandService(AppInstanceService appInstanceService,
                                ArthasCommandFactory commandFactory,
                                ArthasCommandExecutor commandExecutor,
                                ArthasCommandRecordMapper commandRecordMapper) {
        this.appInstanceService = appInstanceService;
        this.commandFactory = commandFactory;
        this.commandExecutor = commandExecutor;
        this.commandRecordMapper = commandRecordMapper;
    }

    public ArthasExecuteResponse execute(ArthasExecuteRequest request) {
        String requestNo = generateRequestNo();

        AppInstance instance = appInstanceService.getOnlineInstance(
                request.getAppId(),
                request.getEnv()
        );

        String command = commandFactory.buildCommand(request.getCommandType());

        ArthasExecuteResponse response = commandExecutor.execute(
                instance,
                requestNo,
                command,
                request.getCommandType()
        );

        saveRecord(request, response);

        return response;
    }

    private void saveRecord(ArthasExecuteRequest request,
                            ArthasExecuteResponse response) {
        ArthasCommandRecord record = new ArthasCommandRecord();
        record.setRequestNo(response.getRequestNo());
        record.setAppId(response.getAppId());
        record.setEnv(response.getEnv());
        record.setCommand(response.getCommand());
        record.setCommandType(request.getCommandType());
        record.setSuccess(response.isSuccess());
        record.setCostMillis(response.getCostMillis());
        record.setOutputExcerpt(excerpt(response.getOutput(), 4000));
        record.setErrorMessage(response.getErrorMessage());
        record.setCreatedAt(LocalDateTime.now());

        commandRecordMapper.insert(record);
    }

    private String excerpt(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private String generateRequestNo() {
        return "ARTHAS-" + UUID.randomUUID().toString().replace("-", "");
    }
}
```

---

## 20. Controller

```java
package com.example.diagnosis.controller;

import com.example.diagnosis.domain.ArthasExecuteRequest;
import com.example.diagnosis.domain.ArthasExecuteResponse;
import com.example.diagnosis.service.ArthasCommandService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/arthas")
public class ArthasCommandController {

    private final ArthasCommandService arthasCommandService;

    public ArthasCommandController(ArthasCommandService arthasCommandService) {
        this.arthasCommandService = arthasCommandService;
    }

    @PostMapping("/execute")
    public ArthasExecuteResponse execute(@Valid @RequestBody ArthasExecuteRequest request) {
        return arthasCommandService.execute(request);
    }

    @GetMapping("/health")
    public ArthasExecuteResponse health(@RequestParam String appId,
                                        @RequestParam String env) {
        ArthasExecuteRequest request = new ArthasExecuteRequest();
        request.setAppId(appId);
        request.setEnv(env);
        request.setCommandType("jvm");
        return arthasCommandService.execute(request);
    }
}
```

---

## 21. 全局异常处理

```java
package com.example.diagnosis.config;

import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException e) {
        return ErrorResponse.of("BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleSecurity(SecurityException e) {
        return ErrorResponse.of("FORBIDDEN", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("参数校验失败");
        return ErrorResponse.of("VALIDATION_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleException(Exception e) {
        return ErrorResponse.of("INTERNAL_ERROR", e.getMessage());
    }

    @Data
    public static class ErrorResponse {
        private String code;
        private String message;

        public static ErrorResponse of(String code, String message) {
            ErrorResponse response = new ErrorResponse();
            response.setCode(code);
            response.setMessage(message);
            return response;
        }
    }
}
```

---

## 22. 接口调用示例

### 22.1 执行 jvm

```bash
curl -X POST http://localhost:9001/api/arthas/execute \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "order-service",
    "env": "test",
    "commandType": "jvm"
  }'
```

---

### 22.2 执行 dashboard

```bash
curl -X POST http://localhost:9001/api/arthas/execute \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "order-service",
    "env": "test",
    "commandType": "dashboard"
  }'
```

---

### 22.3 执行 thread

```bash
curl -X POST http://localhost:9001/api/arthas/execute \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "order-service",
    "env": "test",
    "commandType": "thread"
  }'
```

---

### 22.4 执行 topThread

```bash
curl -X POST http://localhost:9001/api/arthas/execute \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "order-service",
    "env": "test",
    "commandType": "topThread"
  }'
```

---

### 22.5 执行 memory

```bash
curl -X POST http://localhost:9001/api/arthas/execute \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "order-service",
    "env": "test",
    "commandType": "memory"
  }'
```

---

### 22.6 健康检查

```bash
curl "http://localhost:9001/api/arthas/health?appId=order-service&env=test"
```

---

## 23. 返回结果示例

```json
{
  "requestNo": "ARTHAS-0cf0f1d861ff4322b762e6a72e8a50bb",
  "appId": "order-service",
  "env": "test",
  "command": "jvm",
  "success": true,
  "output": "... Arthas jvm output ...",
  "errorMessage": null,
  "costMillis": 281
}
```

---

## 24. 为什么不让前端直接传 command？

不要设计成：

```json
{
  "appId": "order-service",
  "env": "test",
  "command": "thread -n 5"
}
```

原因：

```text
1. 用户可能传入高风险命令。
2. 后续接入 AI Agent 后，大模型可能生成危险命令。
3. 参数不可控。
4. 审计和权限不好做。
5. 生产环境安全风险高。
```

推荐设计：

```json
{
  "appId": "order-service",
  "env": "test",
  "commandType": "topThread"
}
```

后端映射成：

```text
thread -n 5
```

---

## 25. 后续接入 AI Agent 的方式

第一阶段完成后，可以把这些能力包装成 Spring AI / LangChain4j Tool。

示例：

```java
@Tool(description = "查看目标 Java 应用 JVM 信息")
public ArthasExecuteResponse jvmInfo(String appId, String env) {
    return execute(appId, env, "jvm");
}

@Tool(description = "查看目标 Java 应用内存使用情况")
public ArthasExecuteResponse memoryInfo(String appId, String env) {
    return execute(appId, env, "memory");
}

@Tool(description = "查看 CPU 占用最高的线程")
public ArthasExecuteResponse topThread(String appId, String env) {
    return execute(appId, env, "topThread");
}
```

这样 AI Agent 只能选择受控 Tool，而不能直接生成任意 Arthas 命令。

---

## 26. 第一阶段验收标准

完成以下内容，即认为第一阶段第 1 步落地完成：

```text
1. MySQL 能维护 appId、env、ip、arthasHttpPort。
2. 后端提供 /api/arthas/execute 接口。
3. 传 appId + env + commandType 可以执行：
   - dashboard
   - thread
   - topThread
   - jvm
   - memory
4. 命令不是前端直接传，而是 commandType 映射。
5. 后端有命令白名单校验。
6. 每次执行都有 arthas_command_record 审计记录。
7. 目标应用未连接、端口不通、命令失败时能返回错误。
8. 提供 /api/arthas/health 健康检查接口。
```

---

## 27. 建议开发顺序

```text
1. 创建数据库表 app_instance、arthas_command_record。
2. 启动一个测试 Java 应用。
3. 使用 arthas-boot attach 到测试应用。
4. 通过 curl 直接测试 Arthas HTTP API。
5. 编写 AppInstanceMapper 和 AppInstanceService。
6. 编写 ArthasCommandType 和 ArthasCommandFactory。
7. 编写 ArthasCommandGuard。
8. 编写 ArthasHttpCommandExecutor。
9. 编写 ArthasCommandService。
10. 编写 ArthasCommandController。
11. 使用 curl 测试 jvm、memory、thread、topThread、dashboard。
12. 检查 arthas_command_record 是否有审计记录。
```

---

## 28. 第一阶段完成后的架构价值

完成第 1 步后，系统已经具备：

```text
1. 应用实例管理能力。
2. Arthas HTTP 命令执行能力。
3. 命令白名单安全控制。
4. 诊断命令审计能力。
5. 后续 AI Agent Tool Calling 的底座能力。
```

这一步虽然还没有接 AI，但它是整个 AI + Arthas 诊断 Agent 的基础。

后续所有 Agent 能力，例如 CPU 高诊断、线程阻塞诊断、内存异常诊断、接口慢诊断，都会基于这个安全执行网关继续扩展。

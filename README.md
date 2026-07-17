# TeamUp Backend

TeamUp 是面向大学生小组学习与项目协作的平台后端。该服务提供用户与小组管理、任务协作、文档处理、实时通知和 AI 导师能力，并作为前端与协同编辑服务的业务中枢。
此仓库为Java后端,前端仓库地址是[TeamUp-Frontend](https://github.com/Just-so-so-czl/TeamUp-Frontend),文档协同编辑服务仓库地址是[collaboration-service](https://github.com/Just-so-so-czl/collaboration-server)。

## 功能与亮点

### 核心协作能力

- 用户注册、登录和 JWT 鉴权；雪花 ID 在接口中按字符串传递，避免前端精度丢失。
- 小组创建、邀请码加入、入组审核，以及 `Caption`、`Leader`、`Member` 三级角色权限。
- 任务清单、任务创建、认领、分配、完成和截止时间管理。
- 基于 WebSocket 的站内实时通知，连接地址为 `ws://localhost:8080/ws-notify?token=<JWT>`。
- 资料文档上传、下载和解析，支持 PDF、DOCX、Markdown、TXT 等内容抽取；协作文档仅保留资料文档（`type=1`）与协作文档（`type=2`）两类。

### AI 协作导师

- 基于 Spring AI 的流式导师对话（SSE），支持会话历史、文档 `@` 引用和小组协作上下文。
- 文档全文、摘要和协作记忆分层保存：MySQL 管理结构化索引，MongoDB 保存全文、富文本快照和 AI 数据。
- 自适应 Plan + ReAct 运行审计：每次运行与步骤分别记录在 `ai_agent_run`、`ai_agent_step`，前端可恢复并展示工作流状态。
- AI 工具仅能读取小组上下文或生成受控提案。邮件发送、任务清单创建等写操作必须在前端展示可编辑草案，并由用户确认后才调用传统业务 API 执行。
- 协作文档变更由协同服务回调后端；后端通过 Redis 延迟队列防抖，再生成文档摘要与协作记忆，避免编辑过程中的重复模型调用。

### 工程能力

- Spring Boot 3.4、Java 17、MyBatis-Plus、Spring Security、JWT、Spring WebSocket、Spring AMQP、Spring Mail。
- MySQL、MongoDB、Redis、RabbitMQ 分层存储与异步处理；Alibaba Cloud OSS 保存文件对象。
- 统一 `Result<T>` 响应、全局异常处理、OpenAPI/Swagger UI 和 SLF4J 业务日志。

## 技术栈

| 分类 | 技术 |
| --- | --- |
| 运行时 | Java 17、Spring Boot 3.4.1、Maven Wrapper |
| 数据与缓存 | MySQL 8、MongoDB、Redis |
| 消息与实时 | RabbitMQ、Spring WebSocket、SSE |
| AI 与文档 | Spring AI OpenAI、Apache Tika |
| 文件与接口 | Alibaba Cloud OSS、Springdoc OpenAPI |

## 前置依赖

本地启动前请准备以下服务，并检查 `src/main/resources/application.yaml` 中的连接信息：

| 服务 | 默认配置 | 用途 |
| --- | --- | --- |
| MySQL 8 | `localhost:3307`，数据库 `TeamUp` | 业务表、AI 运行与提案记录 |
| MongoDB | `localhost:27017`，数据库 `teamup` | 文档全文、协同快照、AI 记忆 |
| Redis | `localhost:6379` | 会话/运行态缓存、协作文档摘要防抖 |
| RabbitMQ | `localhost:5672` | 异步提醒与文档处理 |
| OSS | 阿里云 OSS 配置 | 上传文件存储 |

邮件和模型服务并非所有页面启动时都需要调用，但使用受控邮件提案或 AI 导师前必须完成相应配置。不要将真实密钥提交到仓库。

## 配置

1. 复制环境变量模板并填入敏感值：

   ```powershell
   Copy-Item .env.example .env
   ```

2. 在 `.env` 中至少配置实际使用的能力：

   ```dotenv
   ALIYUN_OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
   ALIYUN_OSS_ACCESS_KEY_ID=your-access-key-id
   ALIYUN_OSS_ACCESS_KEY_SECRET=your-access-key-secret
   ALIYUN_OSS_BUCKET_NAME=team-up-bucket

   SPRING_AI_OPENAI_BASE_URL=https://api.openai.com
   SPRING_AI_OPENAI_API_KEY=your-model-api-key
   SPRING_AI_OPENAI_CHAT_MODEL=gpt-4o-mini

   MAIL_HOST=smtp.qq.com
   MAIL_PORT=465
   MAIL_PROTOCOL=smtps
   MAIL_USERNAME=your-mail@example.com
   MAIL_PASSWORD=your-mail-authorization-code

   COLLABORATION_SUMMARY_INTERNAL_TOKEN=replace-with-a-long-random-token
   ```

3. 数据库、Redis、MongoDB 和 RabbitMQ 的本地地址目前由 `application.yaml` 管理。请按本机环境修改其主机、端口、用户名和密码。生产环境应通过部署配置覆盖所有密钥和默认内部令牌。

## 初始化数据库

先创建数据库，再执行建表脚本：

```sql
CREATE DATABASE TeamUp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE TeamUp;
```

随后在 MySQL 客户端执行 [`table.sql`](table.sql)。该脚本包含业务表、AI 会话表、Agent 运行审计表和受控提案表；对已有数据库执行前请先备份，并按脚本中的增量 `ALTER TABLE` 注释确认当前表结构。

## 启动

### 使用 Maven Wrapper（推荐）

```powershell
./mvnw.cmd spring-boot:run
```

### 使用本机 Maven

```powershell
mvn spring-boot:run
```

默认 HTTP 服务地址为 `http://localhost:8080`。启动后可访问：

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

### 构建与校验

```powershell
./mvnw.cmd -DskipTests compile
./mvnw.cmd test
./mvnw.cmd clean package
```

## 三服务联调

建议按以下顺序启动：

1. 启动 MySQL、MongoDB、Redis、RabbitMQ，以及按需配置 OSS、邮件和模型服务。
2. 初始化 MySQL 的 `TeamUp` 数据库并执行 `table.sql`。
3. 启动本服务（端口 `8080`）。
4. 启动 `collaboration-server`（默认端口 `1234`），并让其 `COLLABORATION_SUMMARY_INTERNAL_TOKEN` 与本服务一致。
5. 启动 `TeamUp-Frontend`（默认端口 `5173`）。前端当前默认请求 `http://localhost:8080`，协同编辑默认连接 `ws://127.0.0.1:1234`。

协同服务在文档落盘后会请求：

```text
POST /internal/collaboration-summary/content-changed
X-Collaboration-Internal-Token: <shared-token>
```

请仅在受信任的内网中暴露该内部接口，并在生产环境使用高强度随机令牌。

## 目录说明

```text
src/main/java/com/czl/teamupbackend
├── controller/   REST、SSE 与内部回调接口
├── service/      业务、Agent 编排、文档与提醒服务
├── mapper/       MyBatis-Plus Mapper
├── model/        DTO、VO、实体与 MongoDB 文档模型
├── config/       安全、AI 工具、WebSocket、跨域等配置
└── realtime/     在线会话与实时通知
src/main/resources
├── application.yaml
└── mapper/
table.sql          MySQL 初始化与增量表结构
```

## 常见问题

| 现象 | 排查方式 |
| --- | --- |
| 应用启动后数据库不可用 | 核对 `application.yaml` 的 MySQL 端口、数据库名和账号；确认 `TeamUp` 已创建并已执行 `table.sql`。 |
| AI 对话无法响应 | 检查 `SPRING_AI_OPENAI_*` 配置及模型网关可达性；同时确认 MongoDB、Redis 已启动。 |
| 协作文档不生成摘要 | 检查协同服务是否运行、`SUMMARY_CALLBACK_URL` 是否可访问，以及两端内部令牌是否完全一致。 |
| 前端跨域或实时通知失败 | 开发环境需使用 `http://localhost:5173`；WebSocket 连接必须携带登录后的 JWT。 |


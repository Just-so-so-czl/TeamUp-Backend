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

1. 所有配置统一在 [`application.yaml`](src/main/resources/application.yaml) 中维护。本地默认连接 `127.0.0.1`，部署时通过同名环境变量覆盖；不再使用 Spring Profile。

2. 复制 `.env.example` 为 `.env`，只填写密钥和部署变量。`.env` 被 Git 忽略，`application.yaml` 中不保存任何真实凭据：

   ```powershell
   Copy-Item .env.example .env
   ```

3. 本地开发至少配置实际使用的能力：

   ```dotenv
   ALIYUN_OSS_ENDPOINT=oss-cn-hangzhou.aliyuncs.com
   ALIYUN_OSS_ACCESS_KEY_ID=your-access-key-id
   ALIYUN_OSS_ACCESS_KEY_SECRET=your-access-key-secret
   ALIYUN_OSS_BUCKET_NAME=team-up-bucket

   SPRING_AI_OPENAI_BASE_URL=https://api.openai.com
   SPRING_AI_OPENAI_API_KEY=your-model-api-key
   SPRING_AI_OPENAI_CHAT_MODEL=gpt-4o-mini
   SPRING_AI_OPENAI_SUMMARY_MODEL=Qwen3-32B

   MAIL_HOST=smtp.qq.com
   MAIL_PORT=465
   MAIL_PROTOCOL=smtps
   MAIL_USERNAME=your-mail@example.com
   MAIL_PASSWORD=your-mail-authorization-code

   COLLABORATION_SUMMARY_INTERNAL_TOKEN=replace-with-a-long-random-token
   ```

4. `.env` 由 `spring-dotenv` 加载；生产中也可由 Docker、systemd 或 Kubernetes 注入同名环境变量。生产环境必须覆盖基础设施连接信息、`JWT_SECRET`、OSS 密钥，以及以下三组彼此独立的随机内部令牌：

   ```dotenv
   COLLABORATION_SUMMARY_INTERNAL_TOKEN=<random-secret-1>
   COLLABORATION_AGENT_INTERNAL_TOKEN=<random-secret-2>
   COLLABORATION_ACCESS_INTERNAL_TOKEN=<random-secret-3>
   ```

   `JWT_SECRET` 至少为 32 字节。`APP_CORS_ALLOWED_ORIGINS` 只能填写实际前端来源的完整 Origin，例如 `https://app.example.com`；多个来源用英文逗号分隔，不能使用 `*`。Swagger 默认关闭，仅在受保护的运维环境中显式设为 `true`。

## 初始化数据库

先创建数据库，再执行建表脚本：

```sql
CREATE DATABASE TeamUp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE TeamUp;
```

随后在 MySQL 客户端对空数据库执行 [`table.sql`](table.sql)。该脚本包含当前完整的业务表、AI 会话表、Agent 运行审计表和受控提案表，不包含历史 `ALTER TABLE` 迁移。已有数据库升级前必须先备份，并另行编写与实际旧版本对应的迁移脚本；不要直接执行此初始化脚本。

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
4. 启动 `collaboration-server`（默认端口 `1234`）。将其 `COLLABORATION_SUMMARY_INTERNAL_TOKEN`、`COLLABORATION_AGENT_INTERNAL_TOKEN` 和 `COLLABORATION_ACCESS_INTERNAL_TOKEN` 分别配置为与本服务相同的值，并让 `COLLABORATION_ACCESS_VERIFY_URL` 指向本服务的内部地址。
5. 启动 `TeamUp-Frontend`。生产构建前设置 `VITE_API_BASE_URL`、`VITE_WS_NOTIFY_URL` 和 `VITE_HOCUSPOCUS_URL`，其来源域名必须写入 `APP_CORS_ALLOWED_ORIGINS`。

协同服务在文档落盘后会请求：

```text
POST /internal/collaboration-summary/content-changed
X-Collaboration-Internal-Token: <shared-token>
```

请仅在受信任的内网中暴露该内部接口，并在生产环境使用高强度随机令牌。协同访问验证接口 `/internal/collaboration-access/verify` 也只能由协同服务使用，二者均以内部令牌保护，应在反向代理/防火墙层限制来源。

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
| 应用启动后数据库不可用 | 核对 `.env` 或部署环境变量中的 MySQL 地址、数据库名和账号；确认 `TeamUp` 已创建并已执行 `table.sql`。 |
| AI 对话无法响应 | 检查 `SPRING_AI_OPENAI_*` 配置及模型网关可达性；同时确认 MongoDB、Redis 已启动。 |
| 协作文档不生成摘要 | 检查协同服务是否运行、`SUMMARY_CALLBACK_URL` 是否可访问，以及两端内部令牌是否完全一致。 |
| 前端跨域或实时通知失败 | 检查浏览器 Origin 已精确写入 `APP_CORS_ALLOWED_ORIGINS`；WebSocket 连接必须携带登录后的 JWT。 |

# 学成在线（xuecheng-plus）

基于 **Spring Cloud Alibaba** 的在线教育平台，涵盖课程内容管理、媒资管理、课程发布、全文检索、统一认证等业务链路。

> 本项目是一个微服务架构的实践项目，参考「学成在线」课程的业务模型，代码在学习过程中逐步实现。

---

## 一、技术栈

### 基础框架

| 分类 | 技术 | 版本 |
| ---- | ---- | ---- |
| 开发语言 | Java | 1.8 |
| 基础框架 | Spring Boot | 2.3.7.RELEASE |
| 微服务框架 | Spring Cloud | Hoxton.SR9 |
| 微服务扩展 | Spring Cloud Alibaba | 2.2.6.RELEASE |

### 微服务组件

| 技术 | 用途 |
| ---- | ---- |
| Nacos | 服务注册与发现、动态配置中心 |
| Spring Cloud Gateway | API 网关、统一路由 |
| OpenFeign | 服务间远程调用（含 FallbackFactory 降级） |
| Spring Security + OAuth2 | 认证授权 |
| JWT（nimbus-jose） | 无状态令牌签发与校验 |

### 数据与存储

| 技术 | 版本 | 用途 |
| ---- | ---- | ---- |
| MySQL | 8.0.30 | 主数据库 |
| MyBatis-Plus | 3.4.1 | ORM 持久层 |
| Druid | 1.2.8 | 数据库连接池 |
| Redis | — | 验证码存储 |
| Elasticsearch | 7.12.1 | 课程全文检索 |
| MinIO | 8.4.3 | 分布式文件 / 媒资存储 |

### 中间件与任务

| 技术 | 版本 | 用途 |
| ---- | ---- | ---- |
| XXL-Job | 2.3.1 | 分布式任务调度（视频转码、课程发布） |
| RabbitMQ | — | 依赖已引入，消息消费尚未实现 |

### 工具与辅助

Lombok、MapStruct、Fastjson、Swagger、Freemarker、Kaptcha、ZXing、OkHttp、Guava、Log4j2

---

## 二、模块结构

```
xuecheng-plus
├── xuecheng-plus-parent        父工程（统一依赖版本管理）
├── xuecheng-plus-base          公共基础模块
├── xuecheng-plus-gateway       网关服务
├── xuecheng-plus-auth          认证授权服务
├── xuecheng-plus-checkcode     验证码服务
├── xuecheng-plus-content       内容管理服务（api / service / model）
├── xuecheng-plus-media         媒资管理服务（api / service / model）
├── xuecheng-plus-learning      学习中心服务（api / service / model）
├── xuecheng-plus-search        课程搜索服务
├── xuecheng-plus-system        系统管理服务（api / service / model）
├── xuecheng-plus-message-sdk   本地消息表 SDK
└── xuecheng-plus-generator     MyBatis-Plus 代码生成器
```

### 模块说明

- **base**：统一响应 `RestResponse`、全局异常处理、分页参数、通用工具类。
- **gateway**：统一路由入口，预留安全拦截与令牌校验配置。
- **auth**：OAuth2 授权服务器，支持账号密码登录与微信扫码登录，签发 JWT。
- **checkcode**：Kaptcha 图形验证码，验证码存于 Redis。
- **content**：课程基本信息、课程分类、课程计划、课程发布（Freemarker 静态化 + 消息表）。
- **media**：媒资上传、大文件分片上传 / 断点续传 / 秒传、XXL-Job 视频转码。
- **learning**：学习中心（**接口骨架已搭建，业务逻辑尚未实现**）。
- **search**：基于 Elasticsearch 的课程索引与全文检索。
- **system**：数据字典管理。
- **message-sdk**：本地消息表 `mq_message` / `mq_message_history`，提供分片扫描、并发执行、阶段幂等能力。
- **generator**：根据数据库表生成实体、Mapper、Service 等代码。

---

## 三、核心设计

### 3.1 课程发布最终一致性

```
发布接口（本地事务）
  ├─ 写 course_publish
  ├─ 写 mq_message（messageType = course_publish）
  └─ 删除 course_publish_pre
        │
        ▼  XXL-Job 分片调度
CoursePublishTask
  ├─ 阶段1：Freemarker 静态化 → Feign 上传 MinIO
  ├─ 阶段2：Feign 调用搜索服务写入 ES 索引
  └─ 阶段3：写 Redis 缓存（预留）
```

- 业务数据与消息记录在**同一个本地事务**中落库，保证消息一定产生；
- 消息表按 `id % shardTotal = shardIndex` 分片，多执行器不重复取任务；
- `stageState1~3` 记录阶段完成状态，任务重试时跳过已完成阶段；
- 全部阶段成功后迁入历史表并从热表删除。

### 3.2 大文件分片上传

| 接口 | 作用 |
| ---- | ---- |
| `/upload/checkfile` | 按 MD5 查库 + 查 MinIO，实现秒传 |
| `/upload/checkchunk` | 校验分片是否存在，实现断点续传 |
| `/upload/uploadchunk` | 上传单个分片 |
| `/upload/mergechunks` | `composeObject` 服务端合并 + MD5 回验 + 入库 + 清理分片 |

### 3.3 分布式视频转码

- XXL-Job 分片广播获取任务，按 CPU 核数控制并发；
- `UPDATE ... WHERE status IN (1,3)` 条件更新实现分布式抢占，无需分布式锁；
- 失败累加 `fail_count`，达到 3 次不再重试；
- 成功后回写播放地址并迁入历史表。

---

## 四、快速开始

### 4.1 环境要求

| 组件 | 版本 / 说明 |
| ---- | ---- |
| JDK | 1.8+ |
| Maven | 3.6+ |
| MySQL | 8.0+，需创建 `xcplus_content`、`xcplus_media`、`xcplus_system`、`xcplus_users` 等库 |
| Nacos | 2.x，作为注册中心与配置中心 |
| Redis | 5.0+ |
| MinIO | 用于媒资存储，需创建 `video`、`mediafiles` 桶 |
| Elasticsearch | 7.12.x，建议安装 `ik` 中文分词插件 |
| XXL-Job | 2.3.x，需部署调度中心 |
| FFmpeg | 视频转码依赖，需在配置中指定 `videoprocess.ffmpegpath` |

### 4.2 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，至少填写 JWT_SIGNING_KEY、OAUTH_CLIENT_SECRET 和 Nacos 地址
```

Spring Boot 会自动读取环境变量。对应的配置项：

| 环境变量 | 配置项 | 说明 |
| ---- | ---- | ---- |
| `NACOS_ADDR` | `spring.cloud.nacos.server-addr` | Nacos 地址，默认 `127.0.0.1:8848` |
| `JWT_SIGNING_KEY` | `jwt.signing-key` | **必填**，认证服务与资源服务必须一致 |
| `OAUTH_CLIENT_ID` | `oauth.client-id` | 默认 `XcWebApp` |
| `OAUTH_CLIENT_SECRET` | `oauth.client-secret` | **必填** |
| `WEIXIN_APPID` / `WEIXIN_SECRET` | `weixin.appid` / `weixin.secret` | 微信扫码登录（可选） |
| `DB_HOST` / `DB_USERNAME` / `DB_PASSWORD` | 代码生成器数据库 | 仅 `xuecheng-plus-generator` 使用 |

> 数据库、Redis、MinIO、ES 的连接信息建议放在 **Nacos 配置中心**，不要提交到仓库。

### 4.3 构建

```bash
# 编译全部模块
mvn clean package -DskipTests

# 只构建某个服务，例如内容服务
mvn clean package -pl xuecheng-plus-content/xuecheng-plus-content-api -am -DskipTests
```

### 4.4 启动顺序

1. 启动基础中间件：Nacos、MySQL、Redis、MinIO、Elasticsearch、XXL-Job
2. 启动 `gateway`（默认 63010）
3. 启动 `auth-service`、`checkcode`、`content-api`、`media-api`、`search`、`system-api`
4. 启动 `content-service` 等任务执行器（XXL-Job 执行器需在调度中心注册）

各服务端口以 Nacos 配置中心中的 `server.port` 为准。

---

## 五、说明

- 本项目为学习实践项目，`learning` 模块（选课、学习记录）尚未实现业务逻辑。
- 视频转码依赖本机 FFmpeg，临时目录 `D:/App/minio/temp` 为开发环境写死路径，运行前请按需修改。
- 微信扫码登录需要自行申请开放平台账号并配置 `WEIXIN_APPID` / `WEIXIN_SECRET`。

## License

[MIT](LICENSE)

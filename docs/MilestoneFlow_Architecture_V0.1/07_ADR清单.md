# 《MilestoneFlow Pilot MVP V0.1 架构决策记录 ADR 清单》

## 1. 文档信息

| 字段 | 内容 |
|---|---|
| 文档编号 | MF-ADR-INDEX-001 |
| 版本 | V0.1 |
| 状态 | Baseline Candidate |

## 2. ADR 管理规则

- ADR 一经接受不删除；被替代时标记 `Superseded` 并链接新 ADR。
- 状态：`Proposed`、`Accepted`、`Rejected`、`Deprecated`、`Superseded`。
- 重大技术变更必须说明背景、选择、替代方案、正负后果和触发复审条件。
- 安全、数据模型、API 兼容性和部署拓扑类决策必须在实现前接受。

## 3. ADR 总表

| ADR | 标题 | 状态 | 核心结论 |
|---|---|---|---|
| ADR-001 | 采用模块化单体 | Accepted | 单代码库、单数据库，领域模块强边界，不采用微服务 |
| ADR-002 | 前端采用 Vue 3 + TypeScript + Vite | Accepted | 管理端与公开端共享工程、分布局与分包 |
| ADR-003 | 后端采用 Java 21 + Spring Boot 3.5.x | Accepted | 初始锁定 3.5.14，不直接采用 Boot 4.0 |
| ADR-004 | 使用 Spring Modulith 验证模块边界 | Accepted | 使用 1.4.x，与 Boot 3.5 对齐 |
| ADR-005 | 主数据库采用 PostgreSQL 17 | Accepted | 初始锁定 17.10，不采用 PostgreSQL 19 Beta |
| ADR-006 | V0.1 不引入 Redis 和消息队列 | Accepted | 幂等、事件和任务基于 PostgreSQL；达到触发条件再引入 |
| ADR-007 | 认证采用不透明 Access/Refresh Token | Accepted | HttpOnly Cookie、数据库存哈希、Refresh 旋转和重放检测 |
| ADR-008 | 工作空间隔离采用应用强制 + 组合外键 | Accepted | V0.1 不启用 PostgreSQL RLS，保留后续增强 |
| ADR-009 | 公开链接采用高熵能力令牌和 Token Exchange | Accepted | 原始 Token 一次出现，数据库只存哈希，公开会话绑定单对象 |
| ADR-010 | 发布对象采用草稿 + 不可变版本 | Accepted | 报价、交付、变更发布后只追加新版本 |
| ADR-011 | 商业基线采用不可变快照 | Accepted | 已确认报价和变更生成新快照，历史不覆盖 |
| ADR-012 | 跨模块异步使用持久化事件/Outbox | Accepted | 业务与事件同事务，邮件由 Worker 发送 |
| ADR-013 | 文件采用 S3 兼容私有对象存储 | Accepted | 预签名上传下载，文件字节不进数据库 |
| ADR-014 | API 使用 REST + OpenAPI 3.1 + URI v1 | Accepted | `/api/v1`，稳定错误码和幂等规范 |
| ADR-015 | Pilot 部署采用 Docker Compose | Accepted | 单应用主机 + 托管 PostgreSQL/对象存储，不使用 Kubernetes |
| ADR-016 | 可观测性采用 JSON 日志 + Micrometer + OpenTelemetry | Accepted | 日志、指标、Trace 统一关联 Request ID |
| ADR-017 | “任务”采用派生 ActionCenter | Accepted | 不提供通用任务 CRUD，只显示商业下一动作 |
| ADR-018 | “反馈”采用领域归属 + 只读聚合 | Accepted | 原始反馈属于报价/交付/变更，统一视图只读 |
| ADR-019 | API 金额使用十进制字符串 | Accepted | 后端 BigDecimal，数据库 numeric，避免 JavaScript 浮点误差 |
| ADR-020 | 数据库迁移采用 Flyway Expand-Contract | Accepted | 不修改历史迁移，应用回滚不依赖破坏性向下迁移 |

---

## ADR-001｜采用模块化单体

**背景：** 核心流程在报价确认、验收、变更确认和付款时存在跨领域强一致事务。Pilot 团队和流量尚未证明微服务必要。

**决策：** 使用模块化单体。代码按业务域划分，单 PostgreSQL 数据库；API 与 Worker 可分进程部署。

**替代方案：** 微服务、传统分层大单体。

**正面后果：**

- 简化事务和一致性；
- 本地开发、测试、部署成本低；
- 通过模块边界保留未来拆分可能。

**负面后果：**

- 所有模块共享发布节奏；
- 若边界执行不严，会退化为耦合单体。

**控制措施：** Spring Modulith、ArchUnit、模块集成测试、禁止跨模块 Repository。

**复审触发：** 独立扩容或发布需求持续出现，模块团队边界稳定，且拆分收益大于分布式成本。

---

## ADR-002｜前端采用 Vue 3 + TypeScript + Vite

**背景：** 需要桌面管理端和移动优先公开页，团队偏向 Vue 技术栈。

**决策：** Vue 3 Composition API、TypeScript、Vite 8；管理端与公开端同工程、不同 Layout 和路由分包。

**正面后果：** 开发效率高、类型安全、共享设计系统和 API 类型。

**负面后果：** 单工程需要严格控制公开端包体，避免引入后台组件全集。

**控制措施：** 路由级拆包、公开组件独立目录、Bundle Size CI 门禁。

---

## ADR-003｜后端采用 Java 21 + Spring Boot 3.5.x

**背景：** 2026-06-06 时 Spring Boot 4 已稳定，但带来 Spring Framework 7 与生态升级面。项目更重视 Pilot 成熟度和库兼容。

**决策：** Java 21，Spring Boot 3.5.x，初始 3.5.14；仅升级兼容 Patch。Boot 4 在 V0.2 或稳定运行后评估。

**正面后果：** 生态成熟、与 Spring Modulith 1.4 对齐、降低新大版本风险。

**负面后果：** 未来需要计划 Boot 4 升级。

**复审触发：** Boot 3.5 维护周期、关键依赖停止支持或 Boot 4 所需库全部验证完成。

---

## ADR-004｜使用 Spring Modulith

**决策：** 使用 Spring Modulith 1.4.x 做模块建模、结构验证、模块测试和持久化事件支持。

**不使用方式：** 不以框架替代领域设计；模块 API 仍需手工明确。

**后果：** 增加少量框架学习成本，但可把模块依赖和事件可靠性变成自动化门禁。

---

## ADR-005｜主数据库采用 PostgreSQL 17

**背景：** 需要事务、组合约束、JSONB、可靠索引、`SKIP LOCKED` 和对象一致性。

**决策：** PostgreSQL 17.x，初始 17.10。PostgreSQL 18 虽为稳定版本，但 Pilot 优先选择已广泛验证的上一主版本；不使用 19 Beta。

**替代方案：** MySQL 8、PostgreSQL 18。

**正面后果：** 丰富约束和并发能力，适合多租户与 Outbox。

**负面后果：** 团队若更熟悉 MySQL，需要补充 PostgreSQL 运维和 SQL 习惯。

**复审触发：** 托管服务支持、性能测试或 PostgreSQL 17 生命周期要求升级。

---

## ADR-006｜V0.1 不引入 Redis 和消息队列

**决策：**

- 幂等记录、邮件任务、事件发布、租约和逾期 Job 使用 PostgreSQL；
- 非关键短缓存可使用 Caffeine；
- 不使用 Kafka/RabbitMQ/Redis 作为 Pilot 必备依赖。

**理由：** 当前规模低，新增基础设施会增加故障面和部署成本。

**触发引入 Redis：** API 多实例需要集中式限流、会话热点成为数据库瓶颈或缓存一致性有明确收益。

**触发消息队列：** 事件吞吐、独立消费者数量或跨服务解耦已超过数据库队列合理范围。

---

## ADR-007｜认证采用不透明 Token

**决策：** Access/Refresh 均为不透明随机令牌，通过 HttpOnly Cookie 传输，数据库保存哈希；Refresh 每次旋转并检测重放。

**替代方案：** JWT、纯服务器 Session。

**选择理由：** 立即撤销、密码重置失效、多端审计和重放检测更简单；Pilot 无需 JWT 的跨服务优势。

**负面后果：** 每个请求需要会话查询或短时缓存。

---

## ADR-008｜应用隔离 + 组合外键，不启用 RLS

**决策：** 所有业务查询显式 `workspace_id`，数据库使用组合外键和包含租户的唯一约束。V0.1 不启用 PostgreSQL Row Level Security。

**理由：** RLS 与连接池会话变量、后台任务、迁移和调试交互复杂；错误配置可能造成“以为有保护但实际绕过”。

**风险：** 应用遗漏租户条件。

**控制措施：** Repository API 约束、代码扫描、集成测试、组合 FK、越权 E2E。

**复审触发：** 多成员/多工作空间复杂度上升，或安全评审要求数据库第二道行级防线。

---

## ADR-009｜公开链接采用 Token Exchange

**决策：** 高熵 Token 通过 URL Fragment 送达浏览器，前端 POST 交换成短时公开会话 Cookie，并从地址栏移除。数据库仅存 Token 哈希。

**正面后果：** 降低代理日志、Referer、后续请求和第三方脚本泄漏原始 Token 的风险。

**负面后果：** 公开页依赖 JavaScript；接口比直接 `/public/{token}` 多一步。

**控制措施：** 简洁失败页、浏览器兼容测试、公开会话严格绑定单对象。

---

## ADR-010｜草稿 + 不可变版本

**决策：** 报价、交付、变更均将可编辑草稿和发布版本分离。发布后禁止修改正文和历史文件引用；新内容产生新版本。

**正面后果：** 争议可追溯，客户始终针对明确版本操作。

**负面后果：** 数据模型和 UI 比直接更新记录更复杂。

---

## ADR-011｜商业基线不可变快照

**决策：** 每次有效商业条件变化生成新快照；项目详情读取当前快照，历史快照保留来源链。

**替代方案：** 动态聚合所有报价/变更、直接更新项目字段。

**理由：** 快照更适合审计、回溯和并发检查，也避免历史规则变化导致旧结果漂移。

---

## ADR-012｜持久化事件与 Worker

**决策：** 业务事务和事件发布记录同事务提交；Worker 执行邮件和非关键投影。邮件 Provider 调用不在业务事务内。

**正面后果：** 业务成功不依赖外部邮件，事件不因进程崩溃丢失。

**负面后果：** 最终一致性、重试和任务监控需要明确实现。

---

## ADR-013｜S3 兼容私有对象存储

**决策：** 浏览器直传私有对象存储；API 生成预签名 URL 并保存元数据。历史交付引用阻止物理删除。

**替代方案：** 数据库存 BLOB、本地磁盘。

**理由：** 降低应用带宽和磁盘依赖，易扩容与备份。

---

## ADR-014｜REST + OpenAPI 3.1 + URI v1

**决策：** REST JSON，`/api/v1`，稳定错误码，OpenAPI 契约进入 Git。破坏性变更使用 v2。

**负面后果：** 需要纪律维护契约与实现同步。

**控制措施：** CI 契约差异检查、前端类型生成、API 测试。

---

## ADR-015｜Docker Compose Pilot 部署

**决策：** 单应用主机运行 Reverse Proxy、API、Worker；数据库和对象存储优先托管。不使用 Kubernetes。

**复审触发：** 多服务、多节点、自动伸缩和高可用需求使 Compose 运维明显不足。

---

## ADR-016｜统一可观测性

**决策：** Spring Actuator/Micrometer + OpenTelemetry，JSON 日志包含 Request ID、Trace ID、模块和结果；日志、指标、Trace 可关联。

**控制措施：** 脱敏过滤、业务正文不入日志、管理端点不暴露公网。

---

## ADR-017｜任务采用 ActionCenter 派生模型

**决策：** 不开发通用 Todo/任务 CRUD。由项目、报价、交付、变更和应收状态派生“下一动作”。

**理由：** 符合冻结范围的商业工作台，同时避免产品滑向通用项目管理。

---

## ADR-018｜反馈归属于业务域

**决策：** 报价留言、交付修改反馈、变更留言分别由所属模块保存；统一 FeedbackView 只读聚合。

**理由：** 不同反馈具有不同状态语义和事务要求，抽成万能评论会丢失业务约束。

---

## ADR-019｜金额以十进制字符串传输

**决策：** API 使用字符串，Java 使用 BigDecimal，PostgreSQL 使用 numeric。

**理由：** 避免 JavaScript IEEE-754 浮点误差和跨语言序列化歧义。

---

## ADR-020｜Flyway Expand-Contract

**决策：** 数据库迁移只追加；生产采用 Expand、兼容发布、回填、Contract。应用回滚不依赖破坏性 Down Migration。

**理由：** 降低发布和回滚时的数据破坏风险。

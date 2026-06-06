# 《MilestoneFlow Pilot MVP V0.1 架构风险与 POC 结论》

## 1. 文档信息

| 字段 | 内容 |
|---|---|
| 文档编号 | MF-ARCH-RISK-001 |
| 版本 | V0.1 |
| POC 日期 | 2026-06-06 |
| POC 类型 | 架构阶段可执行不变量验证（Python 3 + SQLite） |
| 代码位置 | `poc/test_architecture_poc.py` |
| 输出位置 | `poc/POC_RESULT.txt` |

## 2. 结论摘要

本轮 POC 共执行 7 个测试，全部通过：

```text
Ran 7 tests
OK
```

已验证的架构不变量：

1. 公开链接可使用 32 字节高熵令牌，持久化数据中只出现 SHA-256 哈希。
2. 单对象绑定可明确保存对象类型和对象 ID。
3. 组合外键能够阻止子对象跨工作空间引用父对象。
4. 带工作空间条件的查询不会返回其他工作空间数据。
5. 发布版本可由数据库触发器阻止 UPDATE/DELETE，草稿仍可编辑。
6. 并发重复请求在唯一幂等键保护下只产生一个付款业务副作用，并返回同一结果。
7. 邮件异步失败不会回滚已发布报价，任务可重试后完成。

**重要限制：** 本 POC 是架构不变量的轻量验证，不等同于生产技术栈验收。PostgreSQL 锁行为、Spring 事务传播、Spring Security、对象存储和真实邮件供应商必须在后端实现阶段通过 Testcontainers 与集成环境继续验证。

## 3. POC 设计与结果

### POC-001｜公开链接高熵与只存哈希

**风险：** Token 被数据库、日志或任务表泄漏后可直接访问客户对象。

**设计：**

- 使用 32 字节密码学随机数；
- Base64URL 无填充编码；
- 数据库存储 `sha256(token)`；
- 记录绑定的 `object_type` 和 `object_id`；
- 序列化持久化记录时不出现原始 Token。

**结果：** PASS。

**实现要求：**

- 邮件 Worker 发送时现场签发 Token；
- 原始 Token 不进入 Outbox、日志和错误追踪；
- 反向代理访问日志必须过滤公开 Token；
- 推荐使用 Fragment + Token Exchange。

### POC-002｜工作空间组合外键

**风险：** 即使查询层过滤租户，错误写入仍可能让租户 B 的子记录引用租户 A 的项目。

**设计：**

```sql
FOREIGN KEY(workspace_id, project_id)
REFERENCES project(workspace_id, id)
```

尝试在 `ws-b` 创建指向 `ws-a/p-1` 的报价版本。

**结果：** PASS，数据库拒绝跨租户引用。

**实现要求：**

- PostgreSQL 中父表建立 `(workspace_id, id)` 唯一约束；
- 所有项目级子表采用同类组合外键；
- 文件、审计、公开链接、幂等表也包含 `workspace_id`。

### POC-003｜带租户条件查询

**风险：** 仅按对象 ID 查询造成 IDOR/跨租户数据泄漏。

**设计：** 查询使用 `WHERE workspace_id=? AND id=?`，以另一个工作空间查询已存在对象。

**结果：** PASS，返回空结果。

**实现要求：**

- Repository 不提供无 workspace 的业务查找；
- 跨租户和不存在统一返回 404；
- 使用 ArchUnit/代码审查和安全 E2E 继续约束。

### POC-004｜发布版本不可变

**风险：** 已经发给客户或已确认的报价/交付/变更被覆盖，审计证据失真。

**设计：** 数据库触发器在旧状态属于发布/终态时拒绝 UPDATE，在非 DRAFT 时拒绝 DELETE。

**结果：** PASS；发布版本修改和删除失败，草稿修改成功。

**实现要求：**

- 应用层不提供发布版本更新接口；
- PostgreSQL 触发器作为第二道防线；
- 状态与正文可拆表，允许撤销/替代状态变化但不修改正文快照；
- Flyway 测试验证触发器存在。

### POC-005｜并发幂等付款

**风险：** 浏览器重试、双击或网络代理重放导致重复付款记录和错误余额。

**设计：**

- 幂等表主键：`scope + operation + idem_key`；
- 首次请求创建付款和响应快照；
- 后续相同请求返回快照；
- 16 次并发调用共享同一 Key。

**结果：** PASS；付款表最终 1 条，所有响应返回同一 `paymentId`。

**实现要求：**

- PostgreSQL 使用唯一约束；
- 幂等记录和业务副作用在同一事务；
- 相同 Key 不同请求哈希返回 409；
- Testcontainers 使用真实 PostgreSQL 并发再次验证。

### POC-006｜业务事务与邮件解耦

**风险：** 邮件供应商临时失败导致报价发布事务回滚，或业务成功但任务丢失。

**设计：** 同一事务写 `quote_version=PUBLISHED` 与 `outbox_event=PENDING`；随后模拟 Provider 失败，只将事件改为 `RETRY`，最后重试为 `COMPLETED`。

**结果：** PASS；报价始终保持 PUBLISHED，任务可独立重试。

**实现要求：**

- 业务记录和持久化事件同事务；
- Provider 调用只能由 Worker 执行；
- UI 区分“发布成功”和“邮件失败”；
- 永久失败保留复制新链接能力。

## 4. 风险登记册

| ID | 风险 | 概率 | 影响 | 等级 | 缓解措施 | Owner/窗口 |
|---|---|---:|---:|---|---|---|
| R-001 | Repository 遗漏 workspace 条件导致越权 | 中 | 极高 | P0 | 组合 FK、Repository 规范、越权 E2E、代码扫描 | 后端/测试 |
| R-002 | 公开 Token 出现在代理或第三方日志 | 中 | 极高 | P0 | Fragment Exchange、日志脱敏、无第三方脚本、短时公开会话 | 后端/部署 |
| R-003 | 并发确认重复生成基线或应收 | 中 | 极高 | P0 | 幂等表、唯一约束、行锁/条件更新、并发测试 | 后端/测试 |
| R-004 | 商业基线部分更新 | 低 | 极高 | P0 | 单事务、快照、失败回滚、对账测试 | 后端/数据库 |
| R-005 | 金额精度或币种错误 | 中 | 极高 | P0 | BigDecimal、numeric、字符串 API、Money 值对象 | 全栈/测试 |
| R-006 | 时区跨日导致错误逾期 | 中 | 高 | P0 | UTC + LocalDate + Workspace Zone、边界测试 | 后端/测试 |
| R-007 | 发布版本被误更新 | 低 | 极高 | P0 | 草稿/版本分表、无更新 API、DB 触发器 | 后端/数据库 |
| R-008 | 邮件任务丢失或重复发送 | 中 | 高 | P1 | Outbox、租约、任务幂等、冷却时间、监控 | 后端/部署 |
| R-009 | 文件越权或历史文件被删除 | 中 | 极高 | P0 | 私有桶、引用授权、短签名、历史引用保护 | 后端/测试 |
| R-010 | Worker 多实例重复领取 | 中 | 高 | P1 | `SKIP LOCKED`、租约、消费者幂等 | 后端 |
| R-011 | Spring Boot 3.5 到 4 升级成本 | 中 | 中 | P2 | 依赖隔离、Patch 更新、V0.2 专项 ADR | 架构 |
| R-012 | 不使用 RLS 时应用层缺陷突破 | 中 | 极高 | P0 | 多层约束；Pilot 后复审 RLS | 架构/安全 |
| R-013 | 单应用主机故障 | 中 | 高 | P1 | 外置 DB/对象存储、镜像重建、RTO 演练 | 部署 |
| R-014 | 数据库迁移锁表或无法回滚 | 中 | 高 | P1 | Expand-Contract、Staging 演练、PITR | 数据库/部署 |
| R-015 | Dashboard 聚合查询超时 | 中 | 中 | P2 | Projection、复合索引、慢查询监控 | 后端 |
| R-016 | 公开页移动端完成率不足 | 中 | 高 | P1 | 独立轻量布局、真实设备 E2E、可用性测试 | 前端/测试 |
| R-017 | 通用任务/反馈需求侵入 V0.1 | 高 | 中 | P2 | ActionCenter/FeedbackView 边界、变更管理 | 产品/架构 |
| R-018 | 供应商锁定 | 低 | 中 | P2 | Storage/Email/Observability Adapter | 架构/部署 |

## 5. 尚未完成的生产级 POC

以下必须在编码阶段补齐，未完成前不能进入真实 Pilot：

### POC-NEXT-001｜PostgreSQL 并发与事务

使用 Testcontainers PostgreSQL 17 验证：

- 报价确认 50 并发只有一个基线和一组应收；
- 验收通过和请求修改并发只有一个终态；
- 变更确认时基线已变化返回 `COMMERCIAL_BASELINE_STALE`；
- 付款并发和作废并发余额正确；
- `FOR UPDATE SKIP LOCKED` Worker 不重复领取。

### POC-NEXT-002｜Spring Security SPA

验证：

- Access/Refresh Cookie 属性；
- CSRF 获取、登录后刷新、退出后刷新；
- Refresh 旋转和重放撤销；
- 密码重置后旧会话立即失效；
- 同源部署和生产 CORS 配置。

### POC-NEXT-003｜公开 Token Exchange

验证：

- Fragment Token 不进入服务端初始访问日志；
- Exchange 后地址栏清除；
- 公开会话只访问一个对象；
- 文件下载仅限当前交付引用；
- 撤销、过期、替代和限流；
- iOS Safari/Android Chrome 行为。

### POC-NEXT-004｜对象存储

验证：

- 预签名直传；
- MIME/大小/Checksum 校验；
- 未完成上传不能提交交付；
- 短时下载签名；
- 历史引用文件生命周期策略不删除。

### POC-NEXT-005｜邮件 Provider

验证：

- 临时失败、永久失败和超时分类；
- 指数退避；
- 重试不重复发送已成功邮件；
- 发送时签发公开 Token，任务表不保存原始 Token；
- Provider 故障时复制链接仍可完成流程。

### POC-NEXT-006｜性能

最低基准：

- Dashboard 有 1,000 项目时 P95 < 500ms；
- 公开确认 50 并发 P95 < 2s；
- 付款/变更事务无死锁或可安全重试；
- Worker 积压恢复速度满足告警阈值。

## 6. Go/No-Go 结论

### 架构阶段结论：Go，附带实现门禁

选择的模块化单体、PostgreSQL 强约束、不可变版本、幂等、Outbox、能力令牌和私有对象存储能够覆盖冻结范围。当前没有发现必须引入微服务、消息队列、Redis、Kubernetes 或通用任务系统的证据。

### 进入后端编码前必须冻结

1. 数据表和组合外键草案；
2. 状态机和事务边界；
3. OpenAPI 端点与公开 Token Exchange 变更；
4. 错误码；
5. 幂等表和请求哈希规则；
6. 文件元数据和引用模型；
7. Worker 任务状态和重试规则。

### 进入真实 Pilot 前必须通过

- 全部 `POC-NEXT-*`；
- 8 条重点 E2E；
- 跨租户、跨公开对象和文件越权成功数为 0；
- 备份恢复演练；
- 重复商业副作用为 0；
- 未关闭 P0/P1 阻断缺陷为 0。

# 《MilestoneFlow Pilot MVP V0.1 API 与错误码规范》

## 1. 文档信息

| 字段 | 内容 |
|---|---|
| 文档编号 | MF-API-001 |
| 版本 | V0.1 |
| 规范基线 | REST/JSON、OpenAPI 3.1、URI 主版本号 |

## 2. API 设计目标

1. 支持管理端、公开客户页和自动化测试使用同一稳定契约。
2. 将校验错误、认证失败、越权、状态冲突、并发冲突和通知失败明确区分。
3. 所有高风险写操作可安全重试。
4. 金额、时间、版本和租户语义在前后端一致。
5. API 变更可追踪、可弃用、可并行演进。

## 3. 基础约定

### 3.1 Base URL

```text
/api/v1                      内部 Owner API
/api/v1/public               Client 公开 API
/api/v1/internal/jobs        仅内部进程，不经公网
```

### 3.2 内容类型

```http
Content-Type: application/json
Accept: application/json
```

文件字节不经过普通 JSON API 上传；使用上传意图和对象存储预签名地址。

### 3.3 命名

- URI 使用复数、小写、连字符：`/change-requests`。
- JSON 使用 `camelCase`。
- 枚举使用大写下划线：`PENDING_ACCEPTANCE`。
- ID 使用 UUID 字符串，不暴露数据库自增序号。

## 4. HTTP 方法语义

| 方法 | 用途 | 幂等语义 |
|---|---|---|
| GET | 查询资源 | 幂等 |
| POST | 创建资源或执行领域动作 | 默认非幂等，高风险动作强制 Idempotency-Key |
| PATCH | 修改草稿/可变配置 | 使用 ETag/If-Match 防覆盖 |
| DELETE | V0.1 原则上不用于业务历史 | 历史采用 archive/revoke/void 动作 |

领域动作使用清晰子资源：

```text
POST /quotes/{id}/publish
POST /projects/{id}/archive
POST /payment-records/{id}/void
```

不使用模糊的 `POST /action` 或允许任意状态值的通用接口。

## 5. 版本策略

### 5.1 主版本

- URI 固定 `/api/v1`。
- v1 内允许向后兼容新增字段、可选参数和新端点。
- 删除字段、改变含义、改变必填性或状态语义属于破坏性变更，进入 `/api/v2`。

### 5.2 Patch 与部署版本

API 主版本不等于产品版本。应用通过响应头暴露：

```http
X-API-Version: 1
X-App-Version: 0.1.0+<git-sha>
X-Request-Id: <uuid>
```

### 5.3 弃用

- OpenAPI 标记 `deprecated: true`；
- 文档给出替代端点；
- 响应可带 `Deprecation` 和 `Sunset`；
- 至少跨一个正式产品版本保留兼容窗口。

## 6. 资源模型

### 6.1 通用字段

```json
{
  "id": "4a8d...",
  "createdAt": "2026-06-06T12:30:00Z",
  "updatedAt": "2026-06-06T12:35:00Z",
  "version": 3
}
```

`version` 用于乐观锁。不可变发布版本可以没有 `updatedAt` 业务意义，但保留状态记录更新时间时应与正文快照分离。

### 6.2 Money

```json
{
  "amount": "1250.00",
  "currency": "TWD"
}
```

- `amount` 必须为十进制字符串；
- 禁止科学计数法；
- 后端使用 `BigDecimal`；
- 币种不可从浮点或前端本地格式推断。

### 6.3 时间

| 类型 | 格式 | 示例 |
|---|---|---|
| 技术时间/审计时间 | ISO 8601 UTC Instant | `2026-06-06T12:30:00Z` |
| 业务日期 | ISO LocalDate | `2026-06-30` |
| 工作空间时区 | IANA Zone ID | `Asia/Taipei` |

### 6.4 分页

V0.1 列表使用页码分页：

```text
?page=0&size=20&sort=createdAt,desc
```

响应：

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

`size` 默认 20，最大 100。项目时间线如数据增长明显，可在 v1 新增 cursor 端点而不改变现有语义。

## 7. 请求追踪与幂等

### 7.1 Request ID

客户端可发送：

```http
X-Request-Id: <uuid>
```

后端校验格式；缺失时生成。所有响应、日志、审计和 Trace 使用同一 Request ID。

### 7.2 Idempotency-Key

```http
Idempotency-Key: <uuid-or-high-entropy-string>
```

必须用于：

- 发布报价/交付/变更；
- 客户确认/拒绝/验收；
- 付款记录和作废；
- 提醒发送；
- 可能因网络重试重复创建的重要资源。

### 7.3 幂等结果

- 首次成功：正常 2xx，保存响应快照。
- 同 Key、同请求：返回首次状态码和等价响应，带 `Idempotency-Replayed: true`。
- 同 Key、不同请求：409 `IDEMPOTENCY_KEY_REUSED`。
- 首次仍处理中：409 `IDEMPOTENCY_IN_PROGRESS`，可带 `Retry-After`。

## 8. 并发与条件请求

可编辑草稿返回 ETag：

```http
ETag: "3"
```

更新要求：

```http
If-Match: "3"
```

版本不匹配返回 412 `RESOURCE_VERSION_MISMATCH`。领域状态冲突使用 409，例如已归档项目、旧版本被替代、基线已变化。

## 9. 成功响应

### 9.1 创建资源

```http
HTTP/1.1 201 Created
Location: /api/v1/projects/{id}
```

### 9.2 动作成功

```json
{
  "data": {
    "id": "...",
    "status": "PUBLISHED"
  },
  "meta": {
    "requestId": "..."
  }
}
```

允许直接返回资源对象；项目统一采用一套风格，禁止部分端点包 `data`、部分端点不包。V0.1 选择统一 `data/meta` 信封。

### 9.3 无内容

只有真正无响应体的操作使用 204。高风险动作优先返回最终状态和可恢复信息，不滥用 204。

## 10. 统一错误响应

```json
{
  "timestamp": "2026-06-06T12:30:00Z",
  "status": 409,
  "code": "PROJECT_ARCHIVED",
  "message": "项目已归档，不能继续新增业务记录。",
  "requestId": "1b7e...",
  "path": "/api/v1/projects/.../quotes",
  "fieldErrors": [],
  "details": {
    "resourceId": "..."
  }
}
```

生产环境 `details` 只能包含白名单字段，不得包含 SQL、类名、堆栈、Token、密钥和内部存储路径。

### 10.1 字段错误

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "message": "请求字段校验失败。",
  "requestId": "...",
  "fieldErrors": [
    {
      "field": "paymentSchedule[0].amount",
      "code": "AMOUNT_MUST_BE_POSITIVE",
      "message": "金额必须大于 0。"
    }
  ]
}
```

## 11. HTTP 状态码使用

| HTTP | 场景 |
|---:|---|
| 200 | 查询、动作成功、幂等重放 |
| 201 | 创建资源 |
| 202 | 已接收异步任务，如邮件重试 |
| 204 | 无响应内容的成功操作 |
| 400 | JSON 语法、参数格式错误 |
| 401 | 未认证、会话失效 |
| 403 | 已认证但无权限、CSRF 失败 |
| 404 | 资源不存在或跨租户统一隐藏 |
| 409 | 领域状态、幂等、唯一性、并发动作冲突 |
| 412 | ETag/If-Match 不匹配 |
| 413 | 请求体或文件过大 |
| 415 | 不支持的媒体类型 |
| 422 | 业务字段校验失败 |
| 429 | 限流 |
| 500 | 未知内部错误 |
| 502/503 | 关键依赖不可用；邮件异步失败通常不映射为业务 API 失败 |

## 12. 错误码命名规则

```text
<DOMAIN>_<SPECIFIC_REASON>
```

错误码稳定、英文大写、不可复用不同含义。面向用户的 `message` 可本地化，前端逻辑只依赖 `code`。

## 13. 错误码清单

### 13.1 通用

| Code | HTTP | 说明 |
|---|---:|---|
| `INVALID_REQUEST` | 400 | 请求格式错误 |
| `VALIDATION_FAILED` | 422 | 字段校验失败 |
| `RESOURCE_NOT_FOUND` | 404 | 不存在或不可见 |
| `RESOURCE_VERSION_MISMATCH` | 412 | 乐观锁失败 |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | 缺少幂等键 |
| `IDEMPOTENCY_KEY_REUSED` | 409 | 同 Key 不同请求 |
| `IDEMPOTENCY_IN_PROGRESS` | 409 | 首次请求处理中 |
| `RATE_LIMITED` | 429 | 触发限流 |
| `INTERNAL_ERROR` | 500 | 未知错误 |
| `DEPENDENCY_UNAVAILABLE` | 503 | 必要依赖不可用 |

### 13.2 认证

| Code | HTTP | 说明 |
|---|---:|---|
| `AUTH_INVALID_CREDENTIALS` | 401 | 邮箱或密码错误，统一提示 |
| `AUTH_EMAIL_NOT_VERIFIED` | 403 | 未验证邮箱执行受限动作 |
| `AUTH_SESSION_EXPIRED` | 401 | Access 会话过期 |
| `AUTH_REFRESH_INVALID` | 401 | Refresh 无效/过期 |
| `AUTH_REFRESH_REPLAY_DETECTED` | 401 | 检测到刷新重放，撤销会话族 |
| `AUTH_CSRF_INVALID` | 403 | CSRF Token 缺失或错误 |
| `AUTH_ACCOUNT_DISABLED` | 401 | 账号不可用，外部文案仍最小化 |
| `AUTH_PASSWORD_POLICY_VIOLATION` | 422 | 新密码不符合规则 |
| `AUTH_TOKEN_INVALID_OR_EXPIRED` | 400 | 验证/重置令牌统一错误 |

### 13.3 工作空间与客户

| Code | HTTP |
|---|---:|
| `WORKSPACE_ALREADY_EXISTS` | 409 |
| `WORKSPACE_OWNER_LIMIT_REACHED` | 409 |
| `WORKSPACE_ACCESS_DENIED` | 404 |
| `CLIENT_ARCHIVED` | 409 |
| `CLIENT_HAS_ACTIVE_PROJECTS` | 409 |
| `CLIENT_EMAIL_INVALID` | 422 |

### 13.4 项目与商业基线

| Code | HTTP |
|---|---:|
| `PROJECT_ARCHIVED` | 409 |
| `PROJECT_INVALID_STATUS_TRANSITION` | 409 |
| `PROJECT_HAS_PENDING_ITEMS` | 409 |
| `COMMERCIAL_BASELINE_NOT_FOUND` | 409 |
| `COMMERCIAL_BASELINE_STALE` | 409 |
| `COMMERCIAL_BASELINE_UPDATE_FAILED` | 409 |

### 13.5 报价

| Code | HTTP |
|---|---:|
| `QUOTE_SCOPE_REQUIRED` | 422 |
| `QUOTE_PAYMENT_SCHEDULE_INVALID` | 422 |
| `QUOTE_ALREADY_PUBLISHED` | 409 |
| `QUOTE_VERSION_SUPERSEDED` | 409 |
| `QUOTE_REVOKED` | 409 |
| `QUOTE_EXPIRED` | 409 |
| `QUOTE_ALREADY_DECIDED` | 409 |
| `QUOTE_CONFIRMATION_CONFLICT` | 409 |

### 13.6 里程碑、交付与验收

| Code | HTTP |
|---|---:|
| `MILESTONE_INVALID_STATUS_TRANSITION` | 409 |
| `MILESTONE_DELETE_FORBIDDEN` | 409 |
| `DELIVERY_FILES_NOT_READY` | 409 |
| `DELIVERY_ALREADY_SUBMITTED` | 409 |
| `DELIVERY_VERSION_SUPERSEDED` | 409 |
| `ACCEPTANCE_REASON_REQUIRED` | 422 |
| `ACCEPTANCE_ALREADY_FINAL` | 409 |
| `ACCEPTANCE_CONFLICT` | 409 |

### 13.7 需求变更

| Code | HTTP |
|---|---:|
| `CHANGE_BASELINE_REQUIRED` | 409 |
| `CHANGE_IMPACT_REQUIRED` | 422 |
| `CHANGE_ALREADY_PUBLISHED` | 409 |
| `CHANGE_VERSION_SUPERSEDED` | 409 |
| `CHANGE_REVOKED` | 409 |
| `CHANGE_EXPIRED` | 409 |
| `CHANGE_ALREADY_DECIDED` | 409 |
| `CHANGE_CONFIRMATION_CONFLICT` | 409 |

### 13.8 应收、付款与提醒

| Code | HTTP |
|---|---:|
| `RECEIVABLE_NOT_PAYABLE` | 409 |
| `PAYMENT_AMOUNT_INVALID` | 422 |
| `PAYMENT_CURRENCY_MISMATCH` | 422 |
| `PAYMENT_OVERPAYMENT_CONFIRMATION_REQUIRED` | 409 |
| `PAYMENT_ALREADY_VOIDED` | 409 |
| `PAYMENT_VOID_REASON_REQUIRED` | 422 |
| `REMINDER_NOT_APPLICABLE` | 409 |
| `REMINDER_COOLDOWN_ACTIVE` | 409 |
| `EMAIL_TASK_ALREADY_COMPLETED` | 409 |
| `EMAIL_PERMANENT_FAILURE` | 409 |

### 13.9 公开链接与文件

| Code | HTTP |
|---|---:|
| `PUBLIC_LINK_INVALID` | 404 |
| `PUBLIC_LINK_ACTION_NOT_ALLOWED` | 403 |
| `PUBLIC_LINK_SESSION_EXPIRED` | 401 |
| `PUBLIC_ACTION_ALREADY_COMPLETED` | 409 |
| `FILE_TYPE_NOT_ALLOWED` | 422 |
| `FILE_SIZE_EXCEEDED` | 413 |
| `FILE_UPLOAD_INCOMPLETE` | 409 |
| `FILE_NOT_REFERENCED_BY_PUBLIC_OBJECT` | 404 |
| `FILE_HISTORY_DELETE_FORBIDDEN` | 409 |

公开 Client 页面可将多个内部错误码映射为统一安全文案，不能把具体链接状态直接暴露给匿名用户。

## 14. API 端点基线

### 14.1 认证与工作空间

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/verify-email
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
POST   /api/v1/auth/password-reset-requests
POST   /api/v1/auth/password-resets
GET    /api/v1/auth/me
POST   /api/v1/workspaces
GET    /api/v1/workspaces/current
PATCH  /api/v1/workspaces/current
```

### 14.2 客户与项目

```text
GET    /api/v1/clients
POST   /api/v1/clients
GET    /api/v1/clients/{clientId}
PATCH  /api/v1/clients/{clientId}
POST   /api/v1/clients/{clientId}/archive
POST   /api/v1/clients/{clientId}/restore
GET    /api/v1/projects
POST   /api/v1/projects
GET    /api/v1/projects/{projectId}
PATCH  /api/v1/projects/{projectId}
POST   /api/v1/projects/{projectId}/archive
GET    /api/v1/projects/{projectId}/commercial-baselines/current
GET    /api/v1/projects/{projectId}/timeline
```

### 14.3 报价

```text
POST   /api/v1/projects/{projectId}/quotes
GET    /api/v1/quotes/{quoteId}
PATCH  /api/v1/quotes/{quoteId}/draft
POST   /api/v1/quotes/{quoteId}/publish
POST   /api/v1/quote-versions/{versionId}/send
POST   /api/v1/quote-versions/{versionId}/links
POST   /api/v1/quote-versions/{versionId}/revoke
```

将“草稿”和“发布版本”分开，避免使用同一个 ID 同时表示可变与不可变对象。

### 14.4 里程碑与交付

```text
POST   /api/v1/projects/{projectId}/milestones
PATCH  /api/v1/milestones/{milestoneId}
POST   /api/v1/projects/{projectId}/milestones/reorder
POST   /api/v1/milestones/{milestoneId}/start
POST   /api/v1/milestones/{milestoneId}/deliveries
PATCH  /api/v1/delivery-drafts/{deliveryDraftId}
POST   /api/v1/delivery-drafts/{deliveryDraftId}/submit
POST   /api/v1/delivery-versions/{versionId}/send
POST   /api/v1/delivery-versions/{versionId}/links
```

### 14.5 变更

```text
POST   /api/v1/projects/{projectId}/change-requests
PATCH  /api/v1/change-requests/{changeId}/draft
POST   /api/v1/change-requests/{changeId}/publish
POST   /api/v1/change-versions/{versionId}/send
POST   /api/v1/change-versions/{versionId}/links
POST   /api/v1/change-versions/{versionId}/revoke
```

### 14.6 应收、付款、提醒

```text
GET    /api/v1/receivables
GET    /api/v1/receivables/{receivableId}
POST   /api/v1/receivables/{receivableId}/payments
POST   /api/v1/payment-records/{paymentId}/void
POST   /api/v1/receivables/{receivableId}/reminder-previews
POST   /api/v1/receivables/{receivableId}/reminders
GET    /api/v1/email-tasks/{taskId}
POST   /api/v1/email-tasks/{taskId}/retry
```

### 14.7 文件

```text
POST   /api/v1/files/upload-intents
POST   /api/v1/files/{fileId}/complete
GET    /api/v1/files/{fileId}
POST   /api/v1/files/{fileId}/download-intents
```

### 14.8 公开 API

```text
POST   /api/v1/public/sessions/exchange
GET    /api/v1/public/context
POST   /api/v1/public/quote-decisions
POST   /api/v1/public/delivery-decisions
POST   /api/v1/public/change-decisions
POST   /api/v1/public/files/{fileId}/download-intents
POST   /api/v1/public/session/logout
```

公开会话已绑定唯一对象，因此动作接口不再接受可替换的任意版本 ID。该设计是对需求追踪矩阵中 `/public/{type}/{token}` 候选路径的安全收敛，接口窗口冻结 OpenAPI 时需要同步矩阵。

## 15. 邮件失败响应

发布接口只返回业务发布结果与通知状态：

```json
{
  "data": {
    "versionId": "...",
    "status": "PUBLISHED",
    "notification": {
      "taskId": "...",
      "status": "QUEUED"
    }
  },
  "meta": {"requestId": "..."}
}
```

后续邮件失败不改变 `PUBLISHED`。前端通过邮件任务查询显示“业务已成功、通知失败，可重试或重新生成链接”。

## 16. OpenAPI 管理

- OpenAPI 文件进入 Git，与后端代码同版本。
- CI 检查破坏性契约变更。
- 前端类型和 Mock Server 从契约生成。
- 每个端点标注关联 PRD、用户故事、权限、幂等要求和错误码。
- 生产 Swagger UI 默认关闭或仅内部访问；静态契约文件可作为交付物。

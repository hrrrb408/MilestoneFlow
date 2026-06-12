# MF-BE-B4-001: Milestone Foundation CRUD

## 1. 任务范围

本任务实现 MilestoneFlow Pilot MVP V0.1 的 Milestone 后端基础能力，包括：

- Milestone 数据库结构（V009 迁移）
- Milestone JPA Entity / Repository
- Milestone 创建、列表、详情、基础更新 API
- Project-scoped 归属校验
- Workspace-scoped 数据隔离
- Milestone 审计事件
- Milestone OpenAPI 文档
- 完整的集成测试覆盖

**明确未实现：**

- Task CRUD
- Milestone 完成/重新打开 API（状态工作流）
- Milestone 归档/恢复/删除
- 进度计算
- 活动流/评论/附件
- 甘特图/排序拖拽
- 分页

---

## 2. 数据库结构

### 新增迁移：V009\_\_milestone.sql

milestone 表在 V008 project 表之后新增，用于存储项目内的里程碑信息。

```sql
CREATE TABLE milestone (
    id              uuid            NOT NULL,
    workspace_id    uuid            NOT NULL,
    project_id      uuid            NOT NULL,
    title           varchar(180)    NOT NULL,
    description     text            NULL,
    due_date        date            NULL,
    status          varchar(32)     NOT NULL DEFAULT 'OPEN',
    completed_at    timestamptz     NULL,
    completed_by    uuid            NULL,
    settings        jsonb           NOT NULL DEFAULT '{}',
    version         bigint          NOT NULL DEFAULT 0,
    created_at      timestamptz     NOT NULL DEFAULT now(),
    created_by      uuid            NULL,
    updated_at      timestamptz     NOT NULL DEFAULT now(),
    updated_by      uuid            NULL
);
```

### 字段设计依据

| 字段 | 类型 | 来源 |
|------|------|------|
| title varchar(180) | 数据库设计文档 03_数据库表结构.md §3.6 |
| description text | 数据库设计文档 03_数据库表结构.md §3.6 |
| due_date date | 任务规格 + 数据库设计文档 |
| status varchar(32) | B4-001 最小状态：OPEN / COMPLETED |
| settings jsonb | 与 project 表一致的 JSONB 策略 |
| completed_at/completed_by | 为 B4-002 状态工作流预留 |
| workspace_id + project_id | ADR-BE-006 租户隔离 |

### 约束

- `fk_milestone_workspace`: FK → workspace(id)
- `fk_milestone_project`: FK → project(id)
- `ck_milestone_status`: CHECK (status IN ('OPEN', 'COMPLETED'))
- `ck_milestone_settings`: CHECK (jsonb_typeof(settings) = 'object')
- `ck_milestone_version`: CHECK (version >= 0)
- `fk_milestone_created_by/updated_by/completed_by`: FK → app_user(id)

### 索引

- `idx_milestone_project_status_due`: (project_id, status, due_date ASC NULLS LAST, created_at ASC)
- `idx_milestone_workspace_project`: (workspace_id, project_id)
- `idx_milestone_workspace_created`: (workspace_id, created_at DESC)

### 为什么不创建 Task 表

Task 属于 B5 阶段范围。B4-001 仅完成 Milestone 基础 CRUD。

### 为什么不做进度计算

进度计算依赖 Task 完成状态，属于 B5+ 范围。

### 组合外键说明

V008 project 表没有 `UNIQUE(workspace_id, id)` 约束，因此 V009 使用简单 FK → project(id)，通过应用层严格校验 workspace_id 一致性。不得修改已合并的 V008。

---

## 3. Milestone Entity 映射

```
Milestone extends AuditedEntity
├── id: UUID (UUID v7, IdGenerator)
├── workspaceId: UUID (不使用 @ManyToOne)
├── projectId: UUID (不使用 @ManyToOne)
├── title: String (varchar(180))
├── description: String (text)
├── status: MilestoneStatus (OPEN, COMPLETED)
├── dueDate: LocalDate
├── completedAt: Instant (预留)
├── completedBy: UUID (预留)
├── settings: Map<String, Object> (jsonb)
└── version: long (@Version 乐观锁)
```

遵循 ADR-BE-005（审计字段）、ADR-BE-006（不使用 @ManyToOne 跨模块）。

---

## 4. Milestone API

### 4.1 创建 Milestone

```http
POST /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones
```

- 权限：ACTIVE member
- 项目不得 ARCHIVED
- status 默认 OPEN
- 返回 201 + MilestoneResponse

### 4.2 查询 Milestone 列表

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones?status=OPEN
```

- 权限：ACTIVE member
- ARCHIVED project 仍可查询 milestone（只读）
- 支持 status 过滤
- 排序：due_date ASC NULLS LAST, created_at ASC
- V0.1 不分页（Pilot MVP 单项目里程碑数量较小）

### 4.3 查询 Milestone 详情

```http
GET /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}
```

- 权限：ACTIVE member
- 跨 project/workspace 统一返回 404

### 4.4 更新 Milestone

```http
PATCH /api/v1/workspaces/{workspaceId}/projects/{projectId}/milestones/{milestoneId}
```

- 权限：OWNER only（与 Project 更新策略一致）
- 项目不得 ARCHIVED
- 可更新 title / description / dueDate

---

## 5. Project-scoped 归属

Milestone 必须属于一个 Project。所有 repository 查询包含 workspaceId + projectId，确保：

- 跨 project 访问返回 404
- 不泄漏 milestone 是否存在于其他 project

---

## 6. Workspace-scoped 数据隔离

所有 Milestone 查询包含 workspaceId 作为第一参数（ADR-BE-006）：

- 非 member 无法访问 → 404（不泄漏 workspace 存在性）
- 跨 workspace 访问 → 404
- ACTIVE member 可读取所有 project 下的 milestone
- OWNER 才可更新 milestone

---

## 7. 权限模型

| 操作 | 认证 | 角色 |
|------|------|------|
| 创建 Milestone | 必须 | ACTIVE member |
| 列表 Milestone | 必须 | ACTIVE member |
| 详情 Milestone | 必须 | ACTIVE member |
| 更新 Milestone | 必须 | OWNER |

---

## 8. 状态模型

B4-001 使用最小二状态模型：

```
OPEN       — 创建时默认状态
COMPLETED  — 预留给 B4-002
```

完整状态机（DRAFT → READY → IN_PROGRESS → PENDING_ACCEPTANCE → ACCEPTED/REJECTED 等）将在后续迁移中实现。

本任务不开放状态变更 endpoint。

---

## 9. 错误码

| 错误码 | HTTP | 说明 |
|--------|------|------|
| MILESTONE_NOT_FOUND | 404 | 不存在或跨 project/workspace |
| PROJECT_NOT_FOUND | 404 | Project 不存在或不属于 workspace |
| PROJECT_ARCHIVED | 409 | 在 ARCHIVED project 下创建/更新 |
| WORKSPACE_NOT_FOUND | 404 | Workspace 不存在或非 member |
| WORKSPACE_OWNER_REQUIRED | 403 | 非 OWNER 尝试更新 |
| VALIDATION_FAILED | 422 | 字段校验失败 |

---

## 10. 审计事件

| 事件 | target_type | 说明 |
|------|-------------|------|
| MILESTONE_CREATED | MILESTONE | 创建里程碑 |
| MILESTONE_UPDATED | MILESTONE | 更新里程碑 |

metadata 包含变更标记：titleChanged, descriptionChanged, dueDateChanged。

metadata 不包含：完整 description、email、token、secret。

---

## 11. OpenAPI

所有 milestone endpoint 包含：

- cookieAuth security scheme
- 完整 response schema（MilestoneResponse, MilestoneListResponse）
- 错误响应（401, 403, 404, 409, 422）
- 不使用 JWT Bearer

---

## 12. 测试覆盖

### 集成测试

| 测试类 | 覆盖范围 |
|--------|----------|
| MilestoneFlowIT | CRUD 流程、验证、持久化 |
| MilestoneSecurityIT | 跨用户、跨项目、跨工作空间、匿名 |
| MilestoneConstraintIT | FK、CHECK、status、settings 约束 |
| MilestoneAuditIT | MILESTONE_CREATED/UPDATED 审计事件 |
| OpenApiMilestoneDocumentationIT | endpoint 存在、security scheme、schema |

### 回归验证

- B1 认证回归不破坏
- B2 Workspace 回归不破坏
- B3 Project 回归不破坏
- ArchUnit 架构规则通过

---

## 13. 明确未实现范围

- Task CRUD
- Milestone 完成/重新打开 API
- Milestone 归档/恢复/删除
- 进度计算
- 活动流 API
- 评论/附件
- 分页
- 搜索
- 批量操作
- Redis / JWT / OAuth2

---

## 14. 下一步输入

MF-BE-B4-002：Milestone 完成/重新打开、排序策略与 B4 阶段收口。

此任务不得实现 Task（属于 B5）。

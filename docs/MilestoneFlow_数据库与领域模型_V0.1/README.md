# MilestoneFlow Pilot MVP V0.1 数据库与领域模型交付包

- 编制日期：2026-06-06
- 状态：Database Baseline Candidate
- 依据：已冻结的架构设计、模块边界、认证授权、API 规范、非功能要求、ADR 与 POC 结论

## 交付文件

1. `01_领域模型设计.md`
2. `02_ER图.md`
3. `03_数据库表结构.md`
4. `04_数据字典.md`
5. `05_状态机与枚举说明.md`
6. `06_索引和约束设计.md`
7. `07_数据迁移规范.md`

## 核心结论

- User 是全局身份，WorkspaceMember 是角色与租户关系。
- 所有业务数据通过 `workspace_id` 和组合外键实现数据库级隔离。
- Project 是商业闭环容器；Quotation、Baseline、Delivery、ChangeOrder、Receivable 各自保持聚合边界。
- Task 映射为系统派生 `ActionItemProjection`，不提供通用 Todo CRUD。
- ActivityLog 映射为不可变 `audit_event`。
- Feedback 映射为可重建 `feedback_projection`，原始反馈归所属领域。
- 金额使用 `numeric(19,4)`；时间使用 UTC + Workspace IANA Zone。
- 发布版本、商业基线、最终决定、付款和审计历史不物理删除。
- Flyway 采用 Expand–Migrate–Contract；JPA 使用 `ddl-auto=validate`。

## 待跨窗口确认

后端窗口需确认：JPA 聚合映射、UUID 生成、Money Embeddable、JSONB 映射、组合外键实体映射、Projection 查询技术和审计字段填充方案。确认结果应回写《领域模型设计》或新增 ADR。

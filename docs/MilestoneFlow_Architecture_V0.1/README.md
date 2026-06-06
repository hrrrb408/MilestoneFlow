# MilestoneFlow Pilot MVP V0.1 架构交付包

- 编制日期：2026-06-06
- 状态：Architecture Baseline Candidate
- 输入：Pilot MVP V0.1 冻结需求、用户故事、核心流程、需求追踪矩阵、成功指标与 V0.2 变更规则

## 交付文件

1. `01_系统总体架构设计.md`
2. `02_模块划分与依赖关系.md`
3. `03_认证授权设计.md`
4. `04_部署架构设计.md`
5. `05_API与错误码规范.md`
6. `06_非功能需求实现方案.md`
7. `07_ADR清单.md`
8. `08_架构风险与POC结论.md`
9. `poc/test_architecture_poc.py`
10. `poc/POC_RESULT.txt`

## 核心架构结论

- 架构风格：模块化单体 + API/Worker 分进程。
- 前端：Vue 3 + TypeScript + Vite 8。
- 后端：Java 21 + Spring Boot 3.5.x + Spring Modulith 1.4.x。
- 数据库：PostgreSQL 17.x。
- 文件：S3 兼容私有对象存储。
- 异步：PostgreSQL 持久化事件/Outbox，不引入消息队列。
- 认证：不透明 Access/Refresh Token，HttpOnly Cookie，Refresh 旋转。
- 租户：应用强制 workspace 过滤 + 数据库组合外键。
- 公开链接：高熵能力令牌、数据库只存哈希、Token Exchange、单对象会话。
- 部署：Docker Compose 单应用主机 + 托管 PostgreSQL/对象存储。

## POC 结果

架构阶段轻量 POC：7/7 通过。生产级 PostgreSQL、Spring Security、对象存储、邮件和性能 POC 已在风险文档中列为实现门禁。

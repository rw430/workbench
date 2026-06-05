# 小IC工作台可运行 MVP 复现设计

## 背景

当前目录只有两份复现资料：

- `小IC工作台项目总文档.md`
- `小IC工作台项目复现详细文档.md`

两份文档共同描述的第一阶段目标不是完整复刻生产平台，而是先打通主闭环：

```text
目标输入 -> 意图识别 -> 项目创建 -> Agent 推荐 -> OMA 模板 DAG
-> LLM Executor -> HumanGate -> SSE 推送 -> 交付物 -> REFLECTION
```

本文将复现范围收敛为一个本地可启动、可演示、可测试的 MVP。

## 目标

构建一个“小IC工作台”本地 MVP，使用户可以在页面输入一个产品研发目标，并看到系统完成以下流程：

1. 识别输入属于 `tasks` 模式。
2. 创建 `project` 和协同 `room`。
3. 推荐 PD、DEV、QA、PMO 四类 Agent。
4. 加载 `product_dev_v1_full_v1` OMA DAG 模板。
5. DAG 按依赖推进。
6. HumanGate 节点暂停并等待用户确认。
7. 前端通过 SSE 实时看到任务事件。
8. 用户确认后继续执行后续节点。
9. 生成最终交付物。
10. 生成 `REFLECTION.md` 内容。

## 非目标

以下能力在 MVP 中保留边界和扩展点，但不实现完整生产版本：

- 不实现完整 Agent 市场、组织大盘、版本治理。
- 不实现 TypeScript runtime daemon 和 ED25519 设备注册。
- 不接入真实 OpenAI、poolab、Cursor CLI 或 Codex CLI。
- 不实现完整 RBAC、Mist/BUC、审计和字段级加密。
- 不依赖 MySQL、ZDAS、Redis、nginx 或 Docker。

## 技术方案

### 后端

后端放在 `backend/`：

- 使用 Python + FastAPI。
- 使用 SQLite 保存项目、房间、运行、任务、事件、HumanGate 和交付物。
- 使用 Repository + Service 分层，保持文档要求的工程边界。
- 使用确定性 LLM fallback 生成 PRD、评审总结、测试摘要、最终交付物和复盘内容。
- 使用 FastAPI SSE 端点推送事件。

### 前端

前端放在 `frontend/`：

- 使用 Vite + React + TypeScript。
- 第一屏就是任务工作台，不做营销落地页。
- 页面包含目标输入、模式识别、Agent 推荐、DAG 节点、事件流、HumanGate 确认、交付物和 REFLECTION 预览。
- 通过 HTTP 调用创建项目，通过 `EventSource` 订阅 SSE。

### 模板

模板放在 `templates/dags/product_dev_v1_full_v1.yaml`。

MVP DAG 节点：

1. `need_analysis`：LLM 生成 PRD 草稿。
2. `human_gate_prd`：HumanGate 等待人工确认。
3. `need_review`：LLM 生成评审意见。
4. `prd_final`：LLM 生成 PRD 定稿。
5. `coding`：mock runtime 生成实现摘要。
6. `qa_testing`：mock runtime 生成测试摘要。
7. `delivery_summary`：LLM 生成最终交付物。
8. `reflection`：LLM 生成 REFLECTION。

## API 设计

后端提供以下 MVP API：

- `GET /api/health`：健康检查。
- `POST /api/intent/analyze`：输入目标，返回 mode、置信度和推荐模板。
- `POST /api/projects`：创建项目、room、run、task，并启动可自动推进到 HumanGate 的 OMA 执行。
- `GET /api/projects/{project_id}`：查看项目、run、task、artifact 和 reflection。
- `POST /api/human-gates/{gate_id}/confirm`：确认 HumanGate 并继续 DAG。
- `GET /api/events/stream?run_id=...`：SSE 事件流。

## 数据模型

SQLite MVP 表：

- `projects`：目标、mode、状态、创建时间。
- `rooms`：项目对应的协同房间。
- `agents`：内置 PD、DEV、QA、PMO Agent。
- `orchestrator_runs`：一次 OMA 运行实例。
- `orchestrator_tasks`：DAG 节点、依赖、状态、输入输出和日志。
- `human_gates`：等待确认的人工门禁。
- `runtime_events`：SSE 可恢复事件。
- `artifacts`：最终交付物。
- `reflections`：任务复盘内容。

## OMA 执行规则

任务状态：

- `pending`
- `running`
- `waiting_human`
- `completed`
- `failed`

调度规则：

1. 从模板加载 DAG。
2. 只有依赖节点全部完成后，节点才能运行。
3. LLM 节点使用 deterministic fallback 立即生成输出。
4. Runtime 节点使用 mock/local adapter 生成实现或测试摘要。
5. HumanGate 节点创建 `human_gates` 记录并暂停 run。
6. 用户确认后，HumanGate 节点完成，调度器继续推进剩余节点。
7. 每个状态变化写入 `runtime_events`，并通过 SSE 推送。

## 错误处理

- 目标为空时，API 返回 422。
- 未知项目、run 或 gate 返回 404。
- 重复确认已完成 gate 返回当前状态，不重复执行后续节点。
- DAG 存在环或缺失依赖时，run 进入 `failed`，事件流推送 `run.failed`。
- SSE 连接断开不影响调度，事件已持久化在 SQLite。

## 测试策略

后端必须覆盖：

- 意图识别：产品研发目标命中 `tasks` 和 `product_dev_v1_full_v1`。
- 模板加载：DAG 节点和依赖正确。
- OMA 调度：依赖顺序推进，HumanGate 暂停。
- HumanGate：确认后继续执行并生成最终交付物与 REFLECTION。
- API 闭环：创建项目、查看状态、确认 gate、查看最终结果。

前端必须覆盖：

- 输入目标后能渲染项目、Agent、DAG 和事件。
- HumanGate 出现时确认按钮可调用后端。
- 交付物和 REFLECTION 可展示。

## 启动与验收

后端启动：

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e .[dev]
python -m app.main
```

前端启动：

```powershell
cd frontend
npm install
npm run dev
```

验收命令：

```powershell
cd backend
pytest

cd ..\frontend
npm test
npm run build
```

人工验收：

1. 打开前端本地地址。
2. 输入“帮我设计一个消费金融授信产品的产品研发方案”。
3. 确认页面显示 `tasks` 模式和 `product_dev_v1_full_v1` 模板。
4. 确认 DAG 在 HumanGate 停住。
5. 点击确认后，DAG 执行到完成。
6. 确认最终交付物和 REFLECTION 内容出现。

## 后续扩展点

- 将 SQLite Repository 替换为 MySQL/ZDAS。
- 将 deterministic LLM fallback 替换为真实 LLM Gateway provider。
- 将 mock runtime 替换为 daemon/server-direct runtime provider。
- 增加 Agent Profile、SOUL.md、Skybase mock 和成长中心。
- 增加 RBAC、审计、加密和部署材料。

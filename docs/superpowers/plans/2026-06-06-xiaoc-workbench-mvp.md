# Xiaoc Workbench MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable local Xiaoc Workbench MVP that reproduces the approved loop from goal input to intent routing, project creation, OMA DAG execution, HumanGate confirmation, SSE events, artifact generation, and REFLECTION output.

**Architecture:** The backend is a FastAPI app with SQLite persistence, deterministic LLM/runtime executors, Repository + Service boundaries, and a synchronous OMA scheduler that advances until completion or HumanGate pause. The frontend is a Vite React workbench that calls the API, subscribes to SSE, renders the DAG and events, confirms the HumanGate, and displays the final artifact and REFLECTION.

**Tech Stack:** Python 3.11+, FastAPI, SQLAlchemy, PyYAML, pytest, httpx, Vite, React, TypeScript, Vitest, Testing Library.

---

## Source Requirements

This plan implements the approved spec at:

`docs/superpowers/specs/2026-06-06-xiaoc-workbench-mvp-design.md`

The source documents remain as evidence and should not be changed:

- `小IC工作台项目总文档.md`
- `小IC工作台项目复现详细文档.md`

## File Structure

Create these backend files:

- `backend/pyproject.toml`: package metadata, dependencies, pytest config.
- `backend/app/__init__.py`: package marker.
- `backend/app/main.py`: FastAPI app factory, router registration, CLI entrypoint.
- `backend/app/database.py`: SQLite engine/session creation and schema bootstrap.
- `backend/app/models.py`: SQLAlchemy ORM models for projects, rooms, agents, runs, tasks, gates, events, artifacts, reflections.
- `backend/app/schemas.py`: Pydantic request/response models.
- `backend/app/repositories.py`: repository methods for persistence.
- `backend/app/services/intent.py`: deterministic IntentHint routing.
- `backend/app/services/agents.py`: built-in PD/DEV/QA/PMO recommendations.
- `backend/app/services/templates.py`: YAML DAG template loading and validation.
- `backend/app/services/executors.py`: deterministic LLM and mock runtime executors.
- `backend/app/services/orchestrator.py`: OMA run creation, dependency scheduling, HumanGate pause/resume, artifact/reflection creation.
- `backend/app/api.py`: FastAPI routes and SSE stream response.
- `backend/tests/conftest.py`: test database and app fixtures.
- `backend/tests/test_health.py`: health endpoint.
- `backend/tests/test_intent.py`: intent and agent routing.
- `backend/tests/test_templates.py`: DAG template loading.
- `backend/tests/test_orchestrator.py`: OMA scheduling and HumanGate.
- `backend/tests/test_api_flow.py`: API loop.

Create these template and documentation files:

- `templates/dags/product_dev_v1_full_v1.yaml`: approved MVP DAG.
- `README.md`: local reproduction commands.

Create these frontend files:

- `frontend/package.json`: scripts and dependencies.
- `frontend/index.html`: Vite entry.
- `frontend/src/main.tsx`: React bootstrap.
- `frontend/src/types.ts`: API response types.
- `frontend/src/api.ts`: HTTP and SSE client helpers.
- `frontend/src/App.tsx`: workbench UI.
- `frontend/src/App.css`: responsive workbench styling.
- `frontend/src/App.test.tsx`: frontend behavior tests.
- `frontend/src/test/setup.ts`: Testing Library setup.
- `frontend/vite.config.ts`: Vite + Vitest config.
- `frontend/tsconfig.json`: TS config.

---

### Task 1: Backend Package And Health Endpoint

**Files:**
- Create: `backend/pyproject.toml`
- Create: `backend/app/__init__.py`
- Create: `backend/app/main.py`
- Create: `backend/app/api.py`
- Test: `backend/tests/test_health.py`

- [ ] **Step 1: Write the failing health test**

Create `backend/tests/test_health.py`:

```python
from fastapi.testclient import TestClient

from app.main import create_app


def test_health_endpoint_reports_ok():
    client = TestClient(create_app())

    response = client.get("/api/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "service": "xiaoc-workbench"}
```

- [ ] **Step 2: Run the health test and verify RED**

Run:

```powershell
cd backend
python -m pytest tests/test_health.py -q
```

Expected: FAIL with `ModuleNotFoundError: No module named 'app'`.

- [ ] **Step 3: Add minimal backend package and health route**

Create `backend/pyproject.toml`:

```toml
[project]
name = "xiaoc-workbench-backend"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
  "fastapi>=0.111,<1",
  "uvicorn[standard]>=0.30,<1",
  "sqlalchemy>=2.0,<3",
  "pydantic>=2.7,<3",
  "pyyaml>=6.0,<7",
]

[project.optional-dependencies]
dev = [
  "pytest>=8.2,<9",
  "httpx>=0.27,<1",
]

[tool.pytest.ini_options]
testpaths = ["tests"]
pythonpath = ["."]
```

Create `backend/app/__init__.py`:

```python
"""Xiaoc Workbench backend package."""
```

Create `backend/app/api.py`:

```python
from fastapi import APIRouter

router = APIRouter(prefix="/api")


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "xiaoc-workbench"}
```

Create `backend/app/main.py`:

```python
from fastapi import FastAPI

from app.api import router


def create_app() -> FastAPI:
    app = FastAPI(title="Xiaoc Workbench MVP")
    app.include_router(router)
    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="127.0.0.1", port=8888, reload=True)
```

- [ ] **Step 4: Run the health test and verify GREEN**

Run:

```powershell
cd backend
python -m pytest tests/test_health.py -q
```

Expected: PASS, `1 passed`.

- [ ] **Step 5: Commit Task 1**

```powershell
git add backend/pyproject.toml backend/app/__init__.py backend/app/main.py backend/app/api.py backend/tests/test_health.py
git commit -m "feat: add backend health endpoint"
```

---

### Task 2: Intent Routing And Built-In Agents

**Files:**
- Create: `backend/app/schemas.py`
- Create: `backend/app/services/intent.py`
- Create: `backend/app/services/agents.py`
- Modify: `backend/app/api.py`
- Test: `backend/tests/test_intent.py`

- [ ] **Step 1: Write failing intent and agent tests**

Create `backend/tests/test_intent.py`:

```python
from app.services.agents import recommend_agents
from app.services.intent import analyze_intent


def test_product_development_goal_routes_to_tasks_template():
    result = analyze_intent("帮我设计一个消费金融授信产品的产品研发方案")

    assert result.mode == "tasks"
    assert result.template_id == "product_dev_v1_full_v1"
    assert result.confidence >= 0.9
    assert "产品研发" in result.reason


def test_goal_keyword_routes_to_goal_mode():
    result = analyze_intent("持续跟进这个项目直到上线")

    assert result.mode == "goal"
    assert result.template_id is None


def test_agent_recommendation_contains_core_roles():
    agents = recommend_agents("消费金融授信产品研发")

    roles = [agent.role for agent in agents]
    assert roles == ["PD", "DEV", "QA", "PMO"]
    assert agents[0].name == "需求分析分身"
```

- [ ] **Step 2: Run intent tests and verify RED**

Run:

```powershell
cd backend
python -m pytest tests/test_intent.py -q
```

Expected: FAIL with `ModuleNotFoundError: No module named 'app.services'`.

- [ ] **Step 3: Add schemas, intent service, and agent service**

Create `backend/app/schemas.py`:

```python
from pydantic import BaseModel, Field


class IntentRequest(BaseModel):
    goal: str = Field(min_length=1)


class IntentResult(BaseModel):
    mode: str
    confidence: float
    template_id: str | None
    reason: str


class AgentSummary(BaseModel):
    id: str
    name: str
    role: str
    skills: list[str]
```

Create `backend/app/services/intent.py`:

```python
from app.schemas import IntentResult


def analyze_intent(goal: str) -> IntentResult:
    normalized = goal.strip().lower()
    product_markers = ["产品", "prd", "研发", "需求", "方案", "授信"]
    if any(marker in normalized for marker in product_markers):
        return IntentResult(
            mode="tasks",
            confidence=0.95,
            template_id="product_dev_v1_full_v1",
            reason="命中产品研发任务链路，使用产品研发 OMA 模板。",
        )
    if any(marker in normalized for marker in ["持续", "跟进", "长期", "直到"]):
        return IntentResult(
            mode="goal",
            confidence=0.82,
            template_id=None,
            reason="目标表达包含持续托管语义，路由到 Goal 模式。",
        )
    if any(marker in normalized for marker in ["拆解", "流程", "任务"]):
        return IntentResult(
            mode="dynamic",
            confidence=0.78,
            template_id=None,
            reason="目标需要动态拆解，路由到 Dynamic 模式。",
        )
    return IntentResult(
        mode="agent",
        confidence=0.7,
        template_id=None,
        reason="目标更像单 Agent 咨询，路由到 Agent 模式。",
    )
```

Create `backend/app/services/agents.py`:

```python
from app.schemas import AgentSummary


def recommend_agents(goal: str) -> list[AgentSummary]:
    return [
        AgentSummary(id="agent-pd", name="需求分析分身", role="PD", skills=["需求澄清", "PRD", "业务规则"]),
        AgentSummary(id="agent-dev", name="研发实现分身", role="DEV", skills=["技术方案", "接口设计", "实现摘要"]),
        AgentSummary(id="agent-qa", name="质量验证分身", role="QA", skills=["测试策略", "验收用例", "回归风险"]),
        AgentSummary(id="agent-pmo", name="交付协同分身", role="PMO", skills=["排期", "风险同步", "交付复盘"]),
    ]
```

- [ ] **Step 4: Add intent API route**

Modify `backend/app/api.py`:

```python
from fastapi import APIRouter

from app.schemas import IntentRequest, IntentResult
from app.services.intent import analyze_intent

router = APIRouter(prefix="/api")


@router.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "xiaoc-workbench"}


@router.post("/intent/analyze", response_model=IntentResult)
def analyze_intent_route(request: IntentRequest) -> IntentResult:
    return analyze_intent(request.goal)
```

- [ ] **Step 5: Run tests and verify GREEN**

Run:

```powershell
cd backend
python -m pytest tests/test_health.py tests/test_intent.py -q
```

Expected: PASS, `4 passed`.

- [ ] **Step 6: Commit Task 2**

```powershell
git add backend/app/schemas.py backend/app/services/intent.py backend/app/services/agents.py backend/app/api.py backend/tests/test_intent.py
git commit -m "feat: add intent routing and agent recommendations"
```

---

### Task 3: Product Development DAG Template Loader

**Files:**
- Create: `templates/dags/product_dev_v1_full_v1.yaml`
- Create: `backend/app/services/templates.py`
- Test: `backend/tests/test_templates.py`

- [ ] **Step 1: Write failing template loader tests**

Create `backend/tests/test_templates.py`:

```python
from app.services.templates import load_template


def test_product_template_loads_expected_dag():
    template = load_template("product_dev_v1_full_v1")

    assert template.id == "product_dev_v1_full_v1"
    assert [node.id for node in template.nodes] == [
        "need_analysis",
        "human_gate_prd",
        "need_review",
        "prd_final",
        "coding",
        "qa_testing",
        "delivery_summary",
        "reflection",
    ]
    assert template.node_by_id("human_gate_prd").depends_on == ["need_analysis"]
    assert template.node_by_id("reflection").depends_on == ["delivery_summary"]
```

- [ ] **Step 2: Run template test and verify RED**

Run:

```powershell
cd backend
python -m pytest tests/test_templates.py -q
```

Expected: FAIL with `ModuleNotFoundError: No module named 'app.services.templates'`.

- [ ] **Step 3: Create DAG template YAML**

Create `templates/dags/product_dev_v1_full_v1.yaml`:

```yaml
id: product_dev_v1_full_v1
name: 产品研发完整链路
mode: tasks
nodes:
  - id: need_analysis
    name: 需求分析
    kind: llm/prd_draft
    role: PD
    depends_on: []
  - id: human_gate_prd
    name: PRD 确认
    kind: human_gate
    role: USER
    depends_on: [need_analysis]
  - id: need_review
    name: 需求评审
    kind: llm/review
    role: DEV_QA
    depends_on: [human_gate_prd]
  - id: prd_final
    name: PRD 定稿
    kind: llm/prd_final
    role: PD
    depends_on: [need_review]
  - id: coding
    name: 编码实现摘要
    kind: runtime/execution
    role: DEV
    depends_on: [prd_final]
  - id: qa_testing
    name: 测试验证摘要
    kind: runtime/qa
    role: QA
    depends_on: [coding]
  - id: delivery_summary
    name: 交付汇总
    kind: llm/delivery
    role: PMO
    depends_on: [qa_testing]
  - id: reflection
    name: 任务复盘
    kind: llm/reflection
    role: PMO
    depends_on: [delivery_summary]
```

- [ ] **Step 4: Implement typed template loader**

Create `backend/app/services/templates.py`:

```python
from pathlib import Path

import yaml
from pydantic import BaseModel


class TemplateNode(BaseModel):
    id: str
    name: str
    kind: str
    role: str
    depends_on: list[str]


class DagTemplate(BaseModel):
    id: str
    name: str
    mode: str
    nodes: list[TemplateNode]

    def node_by_id(self, node_id: str) -> TemplateNode:
        for node in self.nodes:
            if node.id == node_id:
                return node
        raise KeyError(node_id)


def template_root() -> Path:
    return Path(__file__).resolve().parents[4] / "templates" / "dags"


def load_template(template_id: str) -> DagTemplate:
    path = template_root() / f"{template_id}.yaml"
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    template = DagTemplate.model_validate(data)
    node_ids = {node.id for node in template.nodes}
    for node in template.nodes:
        missing = set(node.depends_on) - node_ids
        if missing:
            raise ValueError(f"Node {node.id} depends on missing nodes: {sorted(missing)}")
    return template
```

- [ ] **Step 5: Run template tests and verify GREEN**

Run:

```powershell
cd backend
python -m pytest tests/test_templates.py tests/test_intent.py tests/test_health.py -q
```

Expected: PASS, `5 passed`.

- [ ] **Step 6: Commit Task 3**

```powershell
git add templates/dags/product_dev_v1_full_v1.yaml backend/app/services/templates.py backend/tests/test_templates.py
git commit -m "feat: add product development dag template"
```

---

### Task 4: SQLite Persistence And Project Creation

**Files:**
- Create: `backend/app/database.py`
- Create: `backend/app/models.py`
- Create: `backend/app/repositories.py`
- Modify: `backend/app/schemas.py`
- Create: `backend/tests/conftest.py`
- Test: `backend/tests/test_orchestrator.py`

- [ ] **Step 1: Write failing project creation persistence test**

Create `backend/tests/conftest.py`:

```python
import pytest

from app.database import create_session_factory, init_db


@pytest.fixture()
def session_factory(tmp_path):
    database_url = f"sqlite:///{tmp_path / 'xiaoc-test.db'}"
    factory = create_session_factory(database_url)
    init_db(factory)
    return factory
```

Create `backend/tests/test_orchestrator.py`:

```python
from app.repositories import WorkbenchRepository
from app.services.orchestrator import OrchestratorService


def test_create_project_initializes_room_agents_run_and_tasks(session_factory):
    repo = WorkbenchRepository(session_factory)
    service = OrchestratorService(repo)

    result = service.create_project("帮我设计一个消费金融授信产品的产品研发方案")

    assert result.project.mode == "tasks"
    assert result.project.status == "waiting_human"
    assert result.room.name == "消费金融产品研发协同室"
    assert [agent.role for agent in result.agents] == ["PD", "DEV", "QA", "PMO"]
    assert result.run.template_id == "product_dev_v1_full_v1"
    assert len(result.tasks) == 8
    assert result.tasks[0].status == "completed"
    assert result.tasks[1].status == "waiting_human"
```

- [ ] **Step 2: Run orchestrator test and verify RED**

Run:

```powershell
cd backend
python -m pytest tests/test_orchestrator.py -q
```

Expected: FAIL with `ModuleNotFoundError: No module named 'app.database'`.

- [ ] **Step 3: Implement SQLite engine and ORM models**

Create `backend/app/database.py`:

```python
from collections.abc import Callable

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from app.models import Base


def create_session_factory(database_url: str = "sqlite:///./xiaoc_workbench.db") -> Callable[[], Session]:
    engine = create_engine(database_url, connect_args={"check_same_thread": False})
    return sessionmaker(bind=engine, expire_on_commit=False)


def init_db(session_factory: Callable[[], Session]) -> None:
    engine = session_factory.kw["bind"]
    Base.metadata.create_all(engine)
```

Create `backend/app/models.py` with these ORM classes:

```python
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class Project(Base):
    __tablename__ = "projects"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    goal: Mapped[str] = mapped_column(Text)
    mode: Mapped[str] = mapped_column(String(32))
    status: Mapped[str] = mapped_column(String(32), default="pending")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class Room(Base):
    __tablename__ = "rooms"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("projects.id"))
    name: Mapped[str] = mapped_column(String(128))


class Agent(Base):
    __tablename__ = "agents"
    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("projects.id"))
    name: Mapped[str] = mapped_column(String(128))
    role: Mapped[str] = mapped_column(String(32))
    skills: Mapped[str] = mapped_column(Text)


class OrchestratorRun(Base):
    __tablename__ = "orchestrator_runs"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("projects.id"))
    template_id: Mapped[str] = mapped_column(String(128))
    status: Mapped[str] = mapped_column(String(32), default="pending")


class OrchestratorTask(Base):
    __tablename__ = "orchestrator_tasks"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    run_id: Mapped[int] = mapped_column(ForeignKey("orchestrator_runs.id"))
    node_id: Mapped[str] = mapped_column(String(128))
    name: Mapped[str] = mapped_column(String(128))
    kind: Mapped[str] = mapped_column(String(64))
    role: Mapped[str] = mapped_column(String(32))
    depends_on: Mapped[str] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(32), default="pending")
    output: Mapped[str] = mapped_column(Text, default="")
    log: Mapped[str] = mapped_column(Text, default="")


class HumanGate(Base):
    __tablename__ = "human_gates"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    run_id: Mapped[int] = mapped_column(ForeignKey("orchestrator_runs.id"))
    task_id: Mapped[int] = mapped_column(ForeignKey("orchestrator_tasks.id"))
    status: Mapped[str] = mapped_column(String(32), default="waiting")
    prompt: Mapped[str] = mapped_column(Text)


class RuntimeEvent(Base):
    __tablename__ = "runtime_events"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    run_id: Mapped[int] = mapped_column(ForeignKey("orchestrator_runs.id"))
    event_type: Mapped[str] = mapped_column(String(64))
    payload: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=datetime.utcnow)


class Artifact(Base):
    __tablename__ = "artifacts"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("projects.id"))
    run_id: Mapped[int] = mapped_column(ForeignKey("orchestrator_runs.id"))
    content: Mapped[str] = mapped_column(Text)


class Reflection(Base):
    __tablename__ = "reflections"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    project_id: Mapped[int] = mapped_column(ForeignKey("projects.id"))
    run_id: Mapped[int] = mapped_column(ForeignKey("orchestrator_runs.id"))
    content: Mapped[str] = mapped_column(Text)
```

- [ ] **Step 4: Implement repository and project DTO schemas**

Add project response models to `backend/app/schemas.py`:

```python
class ProjectSummary(BaseModel):
    id: int
    goal: str
    mode: str
    status: str


class RoomSummary(BaseModel):
    id: int
    project_id: int
    name: str


class RunSummary(BaseModel):
    id: int
    project_id: int
    template_id: str
    status: str


class TaskSummary(BaseModel):
    id: int
    run_id: int
    node_id: str
    name: str
    kind: str
    role: str
    depends_on: list[str]
    status: str
    output: str
    log: str


class HumanGateSummary(BaseModel):
    id: int
    run_id: int
    task_id: int
    status: str
    prompt: str


class ProjectState(BaseModel):
    project: ProjectSummary
    room: RoomSummary
    agents: list[AgentSummary]
    run: RunSummary
    tasks: list[TaskSummary]
    human_gate: HumanGateSummary | None = None
    artifact: str | None = None
    reflection: str | None = None
```

Create `backend/app/repositories.py` with methods that create and list each ORM record:

```python
from collections.abc import Callable
import json

from sqlalchemy import select
from sqlalchemy.orm import Session

from app import models
from app.schemas import AgentSummary
from app.services.templates import TemplateNode


class WorkbenchRepository:
    def __init__(self, session_factory: Callable[[], Session]):
        self.session_factory = session_factory

    def create_project(self, goal: str, mode: str) -> models.Project:
        with self.session_factory() as session:
            project = models.Project(goal=goal, mode=mode, status="pending")
            session.add(project)
            session.commit()
            session.refresh(project)
            return project

    def create_room(self, project_id: int, name: str) -> models.Room:
        with self.session_factory() as session:
            room = models.Room(project_id=project_id, name=name)
            session.add(room)
            session.commit()
            session.refresh(room)
            return room

    def create_agents(self, project_id: int, agents: list[AgentSummary]) -> list[models.Agent]:
        with self.session_factory() as session:
            rows = [
                models.Agent(id=f"{agent.id}-{project_id}", project_id=project_id, name=agent.name, role=agent.role, skills=json.dumps(agent.skills, ensure_ascii=False))
                for agent in agents
            ]
            session.add_all(rows)
            session.commit()
            return rows

    def create_run_with_tasks(self, project_id: int, template_id: str, nodes: list[TemplateNode]) -> tuple[models.OrchestratorRun, list[models.OrchestratorTask]]:
        with self.session_factory() as session:
            run = models.OrchestratorRun(project_id=project_id, template_id=template_id, status="running")
            session.add(run)
            session.flush()
            tasks = [
                models.OrchestratorTask(
                    run_id=run.id,
                    node_id=node.id,
                    name=node.name,
                    kind=node.kind,
                    role=node.role,
                    depends_on=json.dumps(node.depends_on, ensure_ascii=False),
                )
                for node in nodes
            ]
            session.add_all(tasks)
            session.commit()
            return run, tasks
```

Continue in the same file with update and read helpers:

```python
    def list_tasks(self, run_id: int) -> list[models.OrchestratorTask]:
        with self.session_factory() as session:
            return list(session.scalars(select(models.OrchestratorTask).where(models.OrchestratorTask.run_id == run_id).order_by(models.OrchestratorTask.id)))

    def update_task(self, task_id: int, status: str, output: str = "", log: str = "") -> models.OrchestratorTask:
        with self.session_factory() as session:
            task = session.get(models.OrchestratorTask, task_id)
            task.status = status
            task.output = output
            task.log = log
            session.commit()
            session.refresh(task)
            return task

    def update_project_status(self, project_id: int, status: str) -> None:
        with self.session_factory() as session:
            project = session.get(models.Project, project_id)
            project.status = status
            session.commit()

    def update_run_status(self, run_id: int, status: str) -> None:
        with self.session_factory() as session:
            run = session.get(models.OrchestratorRun, run_id)
            run.status = status
            session.commit()

    def create_human_gate(self, run_id: int, task_id: int, prompt: str) -> models.HumanGate:
        with self.session_factory() as session:
            gate = models.HumanGate(run_id=run_id, task_id=task_id, prompt=prompt, status="waiting")
            session.add(gate)
            session.commit()
            session.refresh(gate)
            return gate

    def set_gate_confirmed(self, gate_id: int) -> models.HumanGate | None:
        with self.session_factory() as session:
            gate = session.get(models.HumanGate, gate_id)
            if gate is None:
                return None
            gate.status = "confirmed"
            session.commit()
            session.refresh(gate)
            return gate

    def get_gate(self, gate_id: int) -> models.HumanGate | None:
        with self.session_factory() as session:
            return session.get(models.HumanGate, gate_id)
```

- [ ] **Step 5: Implement minimal orchestrator service for create_project**

Create `backend/app/services/orchestrator.py`:

```python
import json

from app.models import Agent, Artifact, HumanGate, OrchestratorRun, OrchestratorTask, Project, Reflection, Room
from app.repositories import WorkbenchRepository
from app.schemas import AgentSummary, HumanGateSummary, ProjectState, ProjectSummary, RoomSummary, RunSummary, TaskSummary
from app.services.agents import recommend_agents
from app.services.intent import analyze_intent
from app.services.templates import load_template


class OrchestratorService:
    def __init__(self, repo: WorkbenchRepository):
        self.repo = repo

    def create_project(self, goal: str) -> ProjectState:
        intent = analyze_intent(goal)
        project = self.repo.create_project(goal, intent.mode)
        room = self.repo.create_room(project.id, "消费金融产品研发协同室")
        agents = self.repo.create_agents(project.id, recommend_agents(goal))
        template = load_template(intent.template_id or "product_dev_v1_full_v1")
        run, tasks = self.repo.create_run_with_tasks(project.id, template.id, template.nodes)
        self.repo.update_task(tasks[0].id, "completed", output="PRD 草稿已生成", log="llm/prd_draft executed")
        gate = self.repo.create_human_gate(run.id, tasks[1].id, "请确认 PRD 草稿是否可以进入评审。")
        self.repo.update_task(tasks[1].id, "waiting_human", log="waiting for user confirmation")
        self.repo.update_project_status(project.id, "waiting_human")
        self.repo.update_run_status(run.id, "waiting_human")
        return self._state(project, room, agents, run, self.repo.list_tasks(run.id), gate)

    def _state(
        self,
        project: Project,
        room: Room,
        agents: list[Agent],
        run: OrchestratorRun,
        tasks: list[OrchestratorTask],
        gate: HumanGate | None,
        artifact: Artifact | None = None,
        reflection: Reflection | None = None,
    ) -> ProjectState:
        return ProjectState(
            project=ProjectSummary(id=project.id, goal=project.goal, mode=project.mode, status=project.status),
            room=RoomSummary(id=room.id, project_id=room.project_id, name=room.name),
            agents=[AgentSummary(id=agent.id, name=agent.name, role=agent.role, skills=json.loads(agent.skills)) for agent in agents],
            run=RunSummary(id=run.id, project_id=run.project_id, template_id=run.template_id, status=run.status),
            tasks=[
                TaskSummary(
                    id=task.id,
                    run_id=task.run_id,
                    node_id=task.node_id,
                    name=task.name,
                    kind=task.kind,
                    role=task.role,
                    depends_on=json.loads(task.depends_on),
                    status=task.status,
                    output=task.output,
                    log=task.log,
                )
                for task in tasks
            ],
            human_gate=HumanGateSummary(id=gate.id, run_id=gate.run_id, task_id=gate.task_id, status=gate.status, prompt=gate.prompt) if gate else None,
            artifact=artifact.content if artifact else None,
            reflection=reflection.content if reflection else None,
        )
```

- [ ] **Step 6: Run project creation test and verify GREEN**

Run:

```powershell
cd backend
python -m pytest tests/test_orchestrator.py -q
```

Expected: PASS, `1 passed`.

- [ ] **Step 7: Commit Task 4**

```powershell
git add backend/app/database.py backend/app/models.py backend/app/repositories.py backend/app/schemas.py backend/app/services/orchestrator.py backend/tests/conftest.py backend/tests/test_orchestrator.py
git commit -m "feat: persist projects and initialize oma runs"
```

---

### Task 5: OMA Scheduling, Events, HumanGate Resume, Artifact, Reflection

**Files:**
- Create: `backend/app/services/executors.py`
- Modify: `backend/app/repositories.py`
- Modify: `backend/app/services/orchestrator.py`
- Modify: `backend/tests/test_orchestrator.py`

- [ ] **Step 1: Add failing HumanGate resume test**

Append to `backend/tests/test_orchestrator.py`:

```python
def test_confirm_human_gate_completes_dag_and_generates_outputs(session_factory):
    repo = WorkbenchRepository(session_factory)
    service = OrchestratorService(repo)
    created = service.create_project("帮我设计一个消费金融授信产品的产品研发方案")

    completed = service.confirm_human_gate(created.human_gate.id)

    assert completed.project.status == "completed"
    assert completed.run.status == "completed"
    assert all(task.status == "completed" for task in completed.tasks)
    assert "消费金融授信产品" in completed.artifact
    assert "# 任务复盘" in completed.reflection
```

- [ ] **Step 2: Run HumanGate resume test and verify RED**

Run:

```powershell
cd backend
python -m pytest tests/test_orchestrator.py::test_confirm_human_gate_completes_dag_and_generates_outputs -q
```

Expected: FAIL with `AttributeError: 'OrchestratorService' object has no attribute 'confirm_human_gate'`.

- [ ] **Step 3: Add deterministic executors**

Create `backend/app/services/executors.py`:

```python
def execute_node(kind: str, goal: str, previous_outputs: list[str]) -> str:
    context = "\n".join(output for output in previous_outputs if output)
    if kind == "llm/prd_draft":
        return f"PRD 草稿：围绕{goal}，明确用户、授信流程、风控校验、额度策略和验收口径。"
    if kind == "llm/review":
        return f"评审意见：补充异常流程、数据埋点、合规校验和跨角色交接。上游内容：{context[:120]}"
    if kind == "llm/prd_final":
        return f"PRD 定稿：{goal} 的范围包含需求背景、核心流程、接口边界、验收标准和上线风险。"
    if kind == "runtime/execution":
        return "实现摘要：完成 API 分层、数据模型、OMA 调度、HumanGate 状态流转和前端任务视图。"
    if kind == "runtime/qa":
        return "测试摘要：覆盖意图识别、DAG 依赖、HumanGate 暂停恢复、API 闭环和页面确认动作。"
    if kind == "llm/delivery":
        return f"最终交付物：消费金融授信产品研发方案已形成，包含 PRD、评审、实现、测试和交付总结。目标：{goal}"
    if kind == "llm/reflection":
        return "# 任务复盘\n\n## 任务目标\n完成消费金融授信产品研发方案闭环。\n\n## 可复用经验\n模板化 DAG、HumanGate 和确定性执行器可复用。"
    raise ValueError(f"Unsupported task kind: {kind}")
```

- [ ] **Step 4: Extend repository for events, artifact, reflection, and project lookup**

Add these methods to `backend/app/repositories.py`:

```python
    def get_project_bundle(self, project_id: int):
        with self.session_factory() as session:
            project = session.get(models.Project, project_id)
            if project is None:
                return None
            room = session.scalar(select(models.Room).where(models.Room.project_id == project_id))
            agents = list(session.scalars(select(models.Agent).where(models.Agent.project_id == project_id).order_by(models.Agent.role)))
            run = session.scalar(select(models.OrchestratorRun).where(models.OrchestratorRun.project_id == project_id))
            tasks = list(session.scalars(select(models.OrchestratorTask).where(models.OrchestratorTask.run_id == run.id).order_by(models.OrchestratorTask.id)))
            gate = session.scalar(select(models.HumanGate).where(models.HumanGate.run_id == run.id).order_by(models.HumanGate.id.desc()))
            artifact = session.scalar(select(models.Artifact).where(models.Artifact.run_id == run.id))
            reflection = session.scalar(select(models.Reflection).where(models.Reflection.run_id == run.id))
            return project, room, agents, run, tasks, gate, artifact, reflection

    def create_event(self, run_id: int, event_type: str, payload: dict) -> models.RuntimeEvent:
        with self.session_factory() as session:
            event = models.RuntimeEvent(run_id=run_id, event_type=event_type, payload=json.dumps(payload, ensure_ascii=False))
            session.add(event)
            session.commit()
            session.refresh(event)
            return event

    def list_events(self, run_id: int) -> list[models.RuntimeEvent]:
        with self.session_factory() as session:
            return list(session.scalars(select(models.RuntimeEvent).where(models.RuntimeEvent.run_id == run_id).order_by(models.RuntimeEvent.id)))

    def create_artifact(self, project_id: int, run_id: int, content: str) -> models.Artifact:
        with self.session_factory() as session:
            artifact = models.Artifact(project_id=project_id, run_id=run_id, content=content)
            session.add(artifact)
            session.commit()
            session.refresh(artifact)
            return artifact

    def create_reflection(self, project_id: int, run_id: int, content: str) -> models.Reflection:
        with self.session_factory() as session:
            reflection = models.Reflection(project_id=project_id, run_id=run_id, content=content)
            session.add(reflection)
            session.commit()
            session.refresh(reflection)
            return reflection
```

- [ ] **Step 5: Implement full scheduler resume**

Modify `backend/app/services/orchestrator.py`:

```python
from app.services.executors import execute_node
```

Add `confirm_human_gate`, `get_project_state`, and scheduler helpers:

```python
    def confirm_human_gate(self, gate_id: int) -> ProjectState:
        gate = self.repo.get_gate(gate_id)
        if gate is None:
            raise KeyError(f"human gate not found: {gate_id}")
        if gate.status != "confirmed":
            gate = self.repo.set_gate_confirmed(gate_id)
        self.repo.update_task(gate.task_id, "completed", output="用户确认 PRD 草稿可以进入评审。", log="human gate confirmed")
        self.repo.create_event(gate.run_id, "human_gate.confirmed", {"gate_id": gate.id})
        self._advance_run(gate.run_id)
        project_id = self._project_id_from_run_record(gate.run_id)
        return self.get_project_state(project_id)

    def get_project_state(self, project_id: int) -> ProjectState:
        bundle = self.repo.get_project_bundle(project_id)
        if bundle is None:
            raise KeyError(f"project not found: {project_id}")
        project, room, agents, run, tasks, gate, artifact, reflection = bundle
        return self._state(project, room, agents, run, tasks, gate, artifact, reflection)
```

Replace `create_project` internals after run creation with `_advance_run`:

```python
        self.repo.create_event(run.id, "run.created", {"project_id": project.id, "run_id": run.id})
        self._advance_run(run.id)
        return self.get_project_state(project.id)
```

Add `_advance_run`:

```python
    def _advance_run(self, run_id: int) -> None:
        while True:
            tasks = self.repo.list_tasks(run_id)
            by_node = {task.node_id: task for task in tasks}
            runnable = None
            for task in tasks:
                deps = json.loads(task.depends_on)
                if task.status == "pending" and all(by_node[dep].status == "completed" for dep in deps):
                    runnable = task
                    break
            if runnable is None:
                if all(task.status == "completed" for task in tasks):
                    first_bundle = self.repo.get_project_bundle(self._project_id_from_run_record(run_id))
                    project, room, agents, run, current_tasks, gate, artifact, reflection = first_bundle
                    self.repo.update_run_status(run_id, "completed")
                    self.repo.update_project_status(project.id, "completed")
                return
            self.repo.update_task(runnable.id, "running", log=f"{runnable.kind} started")
            self.repo.create_event(run_id, "run.task.running", {"task_id": runnable.id, "node_id": runnable.node_id})
            if runnable.kind == "human_gate":
                gate = self.repo.create_human_gate(run_id, runnable.id, "请确认 PRD 草稿是否可以进入评审。")
                self.repo.update_task(runnable.id, "waiting_human", log="waiting for user confirmation")
                self.repo.update_run_status(run_id, "waiting_human")
                self.repo.update_project_status(self._project_id_from_run_record(run_id), "waiting_human")
                self.repo.create_event(run_id, "human_gate.waiting", {"gate_id": gate.id, "task_id": runnable.id})
                return
            previous_outputs = [task.output for task in tasks if task.status == "completed"]
            goal = self.get_project_state(self._project_id_from_run_record(run_id)).project.goal
            output = execute_node(runnable.kind, goal, previous_outputs)
            self.repo.update_task(runnable.id, "completed", output=output, log=f"{runnable.kind} completed")
            self.repo.create_event(run_id, "run.task.completed", {"task_id": runnable.id, "node_id": runnable.node_id})
            project_id = self._project_id_from_run_record(run_id)
            if runnable.kind == "llm/delivery":
                self.repo.create_artifact(project_id, run_id, output)
            if runnable.kind == "llm/reflection":
                self.repo.create_reflection(project_id, run_id, output)

    def _project_id_from_run_record(self, run_id: int) -> int:
        with self.repo.session_factory() as session:
            run = session.get(OrchestratorRun, run_id)
            return run.project_id
```

- [ ] **Step 6: Run orchestrator tests and verify GREEN**

Run:

```powershell
cd backend
python -m pytest tests/test_orchestrator.py -q
```

Expected: PASS, `2 passed`.

- [ ] **Step 7: Commit Task 5**

```powershell
git add backend/app/services/executors.py backend/app/repositories.py backend/app/services/orchestrator.py backend/tests/test_orchestrator.py
git commit -m "feat: advance oma runs through human gate"
```

---

### Task 6: Backend API Loop And SSE

**Files:**
- Modify: `backend/app/main.py`
- Modify: `backend/app/api.py`
- Modify: `backend/app/database.py`
- Modify: `backend/app/schemas.py`
- Test: `backend/tests/test_api_flow.py`

- [ ] **Step 1: Write failing API loop test**

Create `backend/tests/test_api_flow.py`:

```python
from fastapi.testclient import TestClient

from app.database import create_session_factory, init_db
from app.main import create_app


def test_api_creates_project_waits_for_gate_and_completes(tmp_path):
    session_factory = create_session_factory(f"sqlite:///{tmp_path / 'api.db'}")
    init_db(session_factory)
    client = TestClient(create_app(session_factory=session_factory))

    created = client.post("/api/projects", json={"goal": "帮我设计一个消费金融授信产品的产品研发方案"})

    assert created.status_code == 200
    state = created.json()
    assert state["project"]["status"] == "waiting_human"
    assert state["run"]["template_id"] == "product_dev_v1_full_v1"
    assert state["human_gate"]["status"] == "waiting"

    project_id = state["project"]["id"]
    gate_id = state["human_gate"]["id"]
    confirmed = client.post(f"/api/human-gates/{gate_id}/confirm")

    assert confirmed.status_code == 200
    completed = confirmed.json()
    assert completed["project"]["status"] == "completed"
    assert completed["artifact"]
    assert completed["reflection"].startswith("# 任务复盘")

    fetched = client.get(f"/api/projects/{project_id}")
    assert fetched.status_code == 200
    assert fetched.json()["project"]["status"] == "completed"


def test_sse_stream_includes_persisted_events(tmp_path):
    session_factory = create_session_factory(f"sqlite:///{tmp_path / 'events.db'}")
    init_db(session_factory)
    client = TestClient(create_app(session_factory=session_factory))
    state = client.post("/api/projects", json={"goal": "消费金融产品研发方案"}).json()

    with client.stream("GET", f"/api/events/stream?run_id={state['run']['id']}") as response:
        body = response.read().decode("utf-8")

    assert response.status_code == 200
    assert "event: run.created" in body
    assert "event: human_gate.waiting" in body
```

- [ ] **Step 2: Run API tests and verify RED**

Run:

```powershell
cd backend
python -m pytest tests/test_api_flow.py -q
```

Expected: FAIL because `/api/projects` returns 404.

- [ ] **Step 3: Add app dependency wiring and API endpoints**

Modify `backend/app/main.py`:

```python
from collections.abc import Callable

from fastapi import FastAPI
from sqlalchemy.orm import Session

from app.api import build_router
from app.database import create_session_factory, init_db


def create_app(session_factory: Callable[[], Session] | None = None) -> FastAPI:
    if session_factory is None:
        session_factory = create_session_factory()
        init_db(session_factory)
    app = FastAPI(title="Xiaoc Workbench MVP")
    app.include_router(build_router(session_factory))
    return app


app = create_app()
```

Replace `backend/app/api.py` with:

```python
from collections.abc import Callable
import json

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from app.repositories import WorkbenchRepository
from app.schemas import IntentRequest, IntentResult, ProjectState
from app.services.intent import analyze_intent
from app.services.orchestrator import OrchestratorService


def build_router(session_factory: Callable[[], Session]) -> APIRouter:
    router = APIRouter(prefix="/api")
    repo = WorkbenchRepository(session_factory)
    orchestrator = OrchestratorService(repo)

    @router.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok", "service": "xiaoc-workbench"}

    @router.post("/intent/analyze", response_model=IntentResult)
    def analyze_intent_route(request: IntentRequest) -> IntentResult:
        return analyze_intent(request.goal)

    @router.post("/projects", response_model=ProjectState)
    def create_project(request: IntentRequest) -> ProjectState:
        return orchestrator.create_project(request.goal)

    @router.get("/projects/{project_id}", response_model=ProjectState)
    def get_project(project_id: int) -> ProjectState:
        try:
            return orchestrator.get_project_state(project_id)
        except KeyError as exc:
            raise HTTPException(status_code=404, detail=str(exc)) from exc

    @router.post("/human-gates/{gate_id}/confirm", response_model=ProjectState)
    def confirm_gate(gate_id: int) -> ProjectState:
        try:
            return orchestrator.confirm_human_gate(gate_id)
        except KeyError as exc:
            raise HTTPException(status_code=404, detail=str(exc)) from exc

    @router.get("/events/stream")
    def stream_events(run_id: int) -> StreamingResponse:
        events = repo.list_events(run_id)

        def generate():
            for event in events:
                yield f"id: {event.id}\n"
                yield f"event: {event.event_type}\n"
                yield f"data: {event.payload}\n\n"

        return StreamingResponse(generate(), media_type="text/event-stream")

    return router
```

- [ ] **Step 4: Keep health tests compatible**

Update `backend/tests/test_health.py` only if needed so it still calls `create_app()` with no args and asserts the same response.

- [ ] **Step 5: Run backend tests and verify GREEN**

Run:

```powershell
cd backend
python -m pytest -q
```

Expected: all backend tests pass.

- [ ] **Step 6: Commit Task 6**

```powershell
git add backend/app/main.py backend/app/api.py backend/app/database.py backend/app/schemas.py backend/tests/test_api_flow.py backend/tests/test_health.py
git commit -m "feat: expose project api and event stream"
```

---

### Task 7: Frontend Workbench

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/types.ts`
- Create: `frontend/src/api.ts`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/App.css`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/App.test.tsx`

- [ ] **Step 1: Write failing frontend test**

Create `frontend/src/App.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import App from "./App";
import * as api from "./api";

describe("Xiaoc workbench", () => {
  it("creates a project and shows the human gate", async () => {
    vi.spyOn(api, "createProject").mockResolvedValue({
      project: { id: 1, goal: "消费金融产品研发方案", mode: "tasks", status: "waiting_human" },
      room: { id: 1, project_id: 1, name: "消费金融产品研发协同室" },
      agents: [
        { id: "agent-pd-1", name: "需求分析分身", role: "PD", skills: ["PRD"] },
        { id: "agent-dev-1", name: "研发实现分身", role: "DEV", skills: ["实现"] },
      ],
      run: { id: 1, project_id: 1, template_id: "product_dev_v1_full_v1", status: "waiting_human" },
      tasks: [
        { id: 1, run_id: 1, node_id: "need_analysis", name: "需求分析", kind: "llm/prd_draft", role: "PD", depends_on: [], status: "completed", output: "PRD 草稿", log: "done" },
        { id: 2, run_id: 1, node_id: "human_gate_prd", name: "PRD 确认", kind: "human_gate", role: "USER", depends_on: ["need_analysis"], status: "waiting_human", output: "", log: "waiting" },
      ],
      human_gate: { id: 1, run_id: 1, task_id: 2, status: "waiting", prompt: "请确认 PRD 草稿是否可以进入评审。" },
      artifact: null,
      reflection: null,
    });

    render(<App />);
    await userEvent.clear(screen.getByLabelText("目标输入"));
    await userEvent.type(screen.getByLabelText("目标输入"), "消费金融产品研发方案");
    await userEvent.click(screen.getByRole("button", { name: "启动任务" }));

    expect(await screen.findByText("tasks")).toBeInTheDocument();
    expect(screen.getByText("product_dev_v1_full_v1")).toBeInTheDocument();
    expect(screen.getByText("需求分析分身")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "确认 HumanGate" })).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Add frontend package config and run RED**

Create `frontend/package.json`:

```json
{
  "name": "xiaoc-workbench-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 127.0.0.1 --port 5173",
    "build": "tsc && vite build",
    "test": "vitest run"
  },
  "dependencies": {
    "@vitejs/plugin-react": "^4.3.0",
    "vite": "^5.4.0",
    "typescript": "^5.5.0",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "lucide-react": "^0.468.0"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.4.0",
    "@testing-library/react": "^16.0.0",
    "@testing-library/user-event": "^14.5.0",
    "vitest": "^2.0.0",
    "jsdom": "^25.0.0"
  }
}
```

Run:

```powershell
cd frontend
npm install
npm test
```

Expected: FAIL with unresolved `./App` or missing implementation.

- [ ] **Step 3: Implement frontend types and API client**

Create `frontend/src/types.ts`:

```ts
export type AgentSummary = { id: string; name: string; role: string; skills: string[] };
export type ProjectSummary = { id: number; goal: string; mode: string; status: string };
export type RoomSummary = { id: number; project_id: number; name: string };
export type RunSummary = { id: number; project_id: number; template_id: string; status: string };
export type TaskSummary = { id: number; run_id: number; node_id: string; name: string; kind: string; role: string; depends_on: string[]; status: string; output: string; log: string };
export type HumanGateSummary = { id: number; run_id: number; task_id: number; status: string; prompt: string };
export type ProjectState = {
  project: ProjectSummary;
  room: RoomSummary;
  agents: AgentSummary[];
  run: RunSummary;
  tasks: TaskSummary[];
  human_gate: HumanGateSummary | null;
  artifact: string | null;
  reflection: string | null;
};
```

Create `frontend/src/api.ts`:

```ts
import type { ProjectState } from "./types";

const API_BASE = import.meta.env.VITE_API_BASE ?? "http://127.0.0.1:8888";

export async function createProject(goal: string): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/projects`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ goal }),
  });
  if (!response.ok) throw new Error(`Create project failed: ${response.status}`);
  return response.json();
}

export async function confirmHumanGate(gateId: number): Promise<ProjectState> {
  const response = await fetch(`${API_BASE}/api/human-gates/${gateId}/confirm`, { method: "POST" });
  if (!response.ok) throw new Error(`Confirm gate failed: ${response.status}`);
  return response.json();
}
```

- [ ] **Step 4: Implement workbench UI**

Create `frontend/src/App.tsx`:

```tsx
import { CheckCircle2, Play, ShieldCheck } from "lucide-react";
import { useState } from "react";

import { confirmHumanGate, createProject } from "./api";
import type { ProjectState } from "./types";
import "./App.css";

const sampleGoal = "帮我设计一个消费金融授信产品的产品研发方案";

export default function App() {
  const [goal, setGoal] = useState(sampleGoal);
  const [state, setState] = useState<ProjectState | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function start() {
    setBusy(true);
    setError(null);
    try {
      setState(await createProject(goal));
    } catch (err) {
      setError(err instanceof Error ? err.message : "启动失败");
    } finally {
      setBusy(false);
    }
  }

  async function confirmGate() {
    if (!state?.human_gate) return;
    setBusy(true);
    try {
      setState(await confirmHumanGate(state.human_gate.id));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="workbench">
      <section className="command">
        <div>
          <h1>小IC工作台</h1>
          <p>目标驱动的多 Agent 任务编排 MVP</p>
        </div>
        <label>
          目标输入
          <textarea value={goal} onChange={(event) => setGoal(event.target.value)} />
        </label>
        <button onClick={start} disabled={busy || !goal.trim()}>
          <Play size={16} /> 启动任务
        </button>
      </section>

      {error && <p className="error">{error}</p>}

      {state && (
        <section className="grid">
          <div className="panel">
            <h2>路由</h2>
            <strong>{state.project.mode}</strong>
            <span>{state.run.template_id}</span>
            <span>{state.room.name}</span>
          </div>
          <div className="panel">
            <h2>Agent Team</h2>
            {state.agents.map((agent) => (
              <div className="agent" key={agent.id}>
                <strong>{agent.name}</strong>
                <span>{agent.role}</span>
              </div>
            ))}
          </div>
          <div className="panel wide">
            <h2>DAG</h2>
            <div className="dag">
              {state.tasks.map((task) => (
                <article className={`task ${task.status}`} key={task.id}>
                  <CheckCircle2 size={16} />
                  <strong>{task.name}</strong>
                  <span>{task.status}</span>
                  {task.output && <p>{task.output}</p>}
                </article>
              ))}
            </div>
          </div>
          {state.human_gate?.status === "waiting" && (
            <div className="panel gate">
              <h2>HumanGate</h2>
              <p>{state.human_gate.prompt}</p>
              <button onClick={confirmGate} disabled={busy}>
                <ShieldCheck size={16} /> 确认 HumanGate
              </button>
            </div>
          )}
          {state.artifact && (
            <div className="panel wide">
              <h2>交付物</h2>
              <p>{state.artifact}</p>
            </div>
          )}
          {state.reflection && (
            <div className="panel wide">
              <h2>REFLECTION</h2>
              <pre>{state.reflection}</pre>
            </div>
          )}
        </section>
      )}
    </main>
  );
}
```

- [ ] **Step 5: Add CSS, Vite, TS, and bootstrap**

Create `frontend/src/App.css`:

```css
body { margin: 0; font-family: Inter, "Microsoft YaHei", system-ui, sans-serif; background: #f6f7f9; color: #1f2933; }
button { display: inline-flex; align-items: center; gap: 6px; border: 0; border-radius: 6px; padding: 10px 14px; background: #2563eb; color: white; cursor: pointer; font-weight: 700; }
button:disabled { opacity: 0.55; cursor: default; }
.workbench { max-width: 1180px; margin: 0 auto; padding: 28px; }
.command { display: grid; grid-template-columns: 1fr; gap: 14px; padding-bottom: 20px; }
.command h1 { margin: 0; font-size: 32px; }
.command p { margin: 6px 0 0; color: #65758b; }
label { display: grid; gap: 8px; font-weight: 700; }
textarea { min-height: 92px; resize: vertical; border: 1px solid #cbd5e1; border-radius: 6px; padding: 12px; font: inherit; }
.grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.panel { background: white; border: 1px solid #dde3ea; border-radius: 8px; padding: 16px; display: grid; gap: 10px; align-content: start; }
.panel h2 { margin: 0; font-size: 17px; }
.wide { grid-column: span 3; }
.agent { display: flex; justify-content: space-between; gap: 12px; border-top: 1px solid #edf1f5; padding-top: 8px; }
.dag { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.task { border: 1px solid #d9e2ec; border-radius: 6px; min-height: 112px; padding: 10px; display: grid; gap: 6px; align-content: start; }
.task.completed { border-color: #16a34a; background: #f0fdf4; }
.task.waiting_human { border-color: #d97706; background: #fffbeb; }
.task p { margin: 0; color: #52616f; font-size: 13px; }
.gate { border-color: #d97706; }
pre { margin: 0; white-space: pre-wrap; font: inherit; }
.error { color: #b91c1c; font-weight: 700; }
@media (max-width: 860px) { .grid, .dag { grid-template-columns: 1fr; } .wide { grid-column: span 1; } }
```

Create the config/bootstrap files:

```tsx
// frontend/src/main.tsx
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

```html
<!-- frontend/index.html -->
<div id="root"></div>
<script type="module" src="/src/main.tsx"></script>
```

```ts
// frontend/vite.config.ts
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
  },
});
```

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["DOM", "DOM.Iterable", "ES2020"],
    "allowJs": false,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "forceConsistentCasingInFileNames": true,
    "module": "ESNext",
    "moduleResolution": "Node",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx"
  },
  "include": ["src"],
  "references": []
}
```

```ts
// frontend/src/test/setup.ts
import "@testing-library/jest-dom/vitest";
```

- [ ] **Step 6: Run frontend tests and build**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: tests pass and build exits 0.

- [ ] **Step 7: Commit Task 7**

```powershell
git add frontend
git commit -m "feat: add xiaoc workbench frontend"
```

---

### Task 8: README, Final Verification, And Local Run

**Files:**
- Create: `README.md`
- Verify: backend and frontend commands.

- [ ] **Step 1: Write README with exact reproduction commands**

Create `README.md`:

```markdown
# 小IC工作台 MVP 复现

本仓库根据当前目录下两份复现文档实现一个本地可运行 MVP：

- 目标输入
- 意图识别
- project + room 创建
- PD/DEV/QA/PMO Agent 推荐
- `product_dev_v1_full_v1` OMA DAG
- HumanGate 暂停和确认
- SSE 事件
- 交付物
- REFLECTION

## 后端

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e .[dev]
python -m app.main
```

后端默认地址：`http://127.0.0.1:8888`

## 前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认地址：`http://127.0.0.1:5173`

## 验证

```powershell
cd backend
python -m pytest -q

cd ..\frontend
npm test
npm run build
```

## 人工复现

1. 打开前端。
2. 输入 `帮我设计一个消费金融授信产品的产品研发方案`。
3. 点击 `启动任务`。
4. 确认页面显示 `tasks` 和 `product_dev_v1_full_v1`。
5. 在 HumanGate 出现后点击确认。
6. 查看 DAG 完成、交付物出现、REFLECTION 出现。
```

- [ ] **Step 2: Run complete backend verification**

Run:

```powershell
cd backend
python -m pytest -q
```

Expected: all backend tests pass.

- [ ] **Step 3: Run complete frontend verification**

Run:

```powershell
cd frontend
npm test
npm run build
```

Expected: frontend tests pass and build exits 0.

- [ ] **Step 4: Smoke start backend**

Run:

```powershell
cd backend
python -m app.main
```

Expected: uvicorn starts on `http://127.0.0.1:8888`. Stop it with `Ctrl+C` after confirming startup.

- [ ] **Step 5: Smoke start frontend**

Run:

```powershell
cd frontend
npm run dev
```

Expected: Vite starts on `http://127.0.0.1:5173`. Stop it with `Ctrl+C` after confirming startup.

- [ ] **Step 6: Commit Task 8**

```powershell
git add README.md
git commit -m "docs: add local reproduction guide"
```

---

## Self-Review

Spec coverage:

- Goal input: Task 7 UI and Task 6 API.
- Intent recognition: Task 2 and Task 6.
- Project + room: Task 4 and Task 6.
- Agent recommendations: Task 2 and Task 4.
- Template DAG: Task 3.
- DAG dependency execution: Task 5.
- HumanGate pause and confirmation: Task 5 and Task 6.
- SSE persisted events: Task 5 and Task 6.
- Artifact and REFLECTION: Task 5, Task 6, and Task 7.
- Startup and verification: Task 8.

Type consistency:

- `ProjectState` is the shared backend response and frontend view model.
- `human_gate` is nullable in API responses and checked before confirmation.
- Task status values are `pending`, `running`, `waiting_human`, `completed`, and `failed`.
- Template id is consistently `product_dev_v1_full_v1`.

Execution note:

- Use Task order exactly.
- Do not stage the two original Chinese source Markdown files unless the user explicitly asks to include them.

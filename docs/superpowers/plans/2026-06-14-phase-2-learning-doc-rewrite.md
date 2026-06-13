# Phase 2 Learning Document Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for this docs-only plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Phase 2 learning document with a detailed beginner-friendly teaching guide.

**Architecture:** This is a documentation-only change. The document will teach concepts first, then walk through the existing Phase 2 backend implementation file by file and request by request.

**Tech Stack:** Markdown, Java 21, Spring Boot 3.3.6, Spring MVC, Spring Data JPA, Flyway/PostgreSQL, JUnit 5, MockMvc, Testcontainers.

---

## File Structure

- Create: `docs/superpowers/specs/2026-06-14-phase-2-learning-doc-rewrite-design.md`
  - Records the approved teaching design.
- Create: `docs/superpowers/plans/2026-06-14-phase-2-learning-doc-rewrite.md`
  - Records the execution plan for the docs-only rewrite.
- Modify: `docs/learning/phase-02-intent-agent-project.md`
  - Replace the terse summary with a beginner-friendly tutorial.

---

### Task 1: Rewrite Learning Document

**Files:**
- Modify: `docs/learning/phase-02-intent-agent-project.md`

- [ ] **Step 1: Replace the existing document**

Write a complete Markdown guide with these required sections:

```markdown
# Phase 02: Intent + Agent + Project 创建

## 怎么使用这份文档
## 学习目标
## 业务背景
## 先建立最小后端概念
## 核心概念
## 设计原因
## 端到端流程
## 代码导读
## 边界条件
## 测试说明
## 排错手册
## 面试讲法
```

- [ ] **Step 2: Include concrete file references**

The guide must reference these files:

```text
backend-java/src/main/java/com/xiaoc/workbench/intent/service/IntentAnalysisService.java
backend-java/src/main/java/com/xiaoc/workbench/intent/api/IntentController.java
backend-java/src/main/java/com/xiaoc/workbench/agent/service/AgentRecommendationService.java
backend-java/src/main/java/com/xiaoc/workbench/agent/service/BuiltinAgentSeeder.java
backend-java/src/main/java/com/xiaoc/workbench/orchestrator/template/DagTemplateLoader.java
backend-java/src/main/java/com/xiaoc/workbench/project/service/ProjectApplicationService.java
backend-java/src/main/java/com/xiaoc/workbench/project/api/ProjectController.java
backend-java/src/main/java/com/xiaoc/workbench/common/web/ApiExceptionHandler.java
templates/dags/credit_card_installment_campaign_v1.yaml
```

- [ ] **Step 3: Include verification commands**

The guide must include:

```powershell
cd backend-java
mvn test
```

### Task 2: Verify and Commit

**Files:**
- Verify all files changed by Task 1.

- [ ] **Step 1: Scan for placeholders**

Run:

```powershell
Select-String -Path .\docs\learning\phase-02-intent-agent-project.md -Pattern '<placeholder-marker-1>','<placeholder-marker-2>'
```

Expected: no matches.

- [ ] **Step 2: Confirm required headings**

Run:

```powershell
Select-String -Path .\docs\learning\phase-02-intent-agent-project.md -Pattern '^## 学习目标','^## 业务背景','^## 核心概念','^## 设计原因','^## 端到端流程','^## 代码导读','^## 边界条件','^## 测试说明','^## 排错手册','^## 面试讲法'
```

Expected: all ten headings are present.

- [ ] **Step 3: Run backend tests**

Run:

```powershell
cd backend-java
mvn test
```

Expected: build success, 31 tests, 0 failures, 0 errors.

- [ ] **Step 4: Commit**

Run:

```powershell
git add docs/superpowers/specs/2026-06-14-phase-2-learning-doc-rewrite-design.md docs/superpowers/plans/2026-06-14-phase-2-learning-doc-rewrite.md docs/learning/phase-02-intent-agent-project.md
git commit -m "docs: rewrite phase 2 learning guide"
```

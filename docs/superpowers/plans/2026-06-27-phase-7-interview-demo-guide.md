# Phase 7 Interview Demo Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add interview-ready demo and explanation documentation for the completed Xiaoc workbench.

**Architecture:** This is a documentation-only phase. The learning guide teaches how to explain the system; the runbook gives exact commands and expected observations for live demos. The docs reference existing Phase 2-6 capabilities without changing application behavior.

**Tech Stack:** Markdown, Spring Boot, React/TypeScript, PostgreSQL, Redis, RabbitMQ, Docker Compose, Maven, npm.

---

## File Structure

- Create: `docs/learning/phase-07-interview-demo-guide.md`
  - Teaching guide for interview explanation, project pitch, architecture story, technical highlights, Q&A, and recovery tactics.
- Create: `docs/runbooks/interview-demo.md`
  - Operational runbook with setup, local queue demo, rabbit queue demo, expected outputs, and shutdown.
- Modify: `docs/runbooks/README.md`
  - Add `interview-demo.md` to the runbook index.

---

## Task 1: Write Interview Learning Guide

**Files:**
- Create: `docs/learning/phase-07-interview-demo-guide.md`

- [ ] **Step 1: Create the guide**

Write a Chinese teaching document with these sections:

```markdown
# Phase 07: Interview Demo Guide

## 怎么使用这份文档
## 学习目标
## 一句话项目介绍
## 三分钟讲项目
## 十分钟演示路线
## 架构讲法
## 按阶段解释亮点
## 技术追问回答
## 现场演示心法
## Demo 失败时怎么救场
## 简历表述建议
## 自查清单
```

- [ ] **Step 2: Scan for incomplete markers**

Run:

```powershell
Select-String -Path docs\learning\phase-07-interview-demo-guide.md -Pattern 'T[B]D','TO[D]O','late[r] fill','placeholde[r]'
```

Expected: no output.

- [ ] **Step 3: Commit**

```powershell
git add docs/learning/phase-07-interview-demo-guide.md
git commit -m "docs: add phase 7 interview guide"
```

---

## Task 2: Write Demo Runbook

**Files:**
- Create: `docs/runbooks/interview-demo.md`
- Modify: `docs/runbooks/README.md`

- [ ] **Step 1: Create runbook**

Write `docs/runbooks/interview-demo.md` with these sections:

```markdown
# Interview Demo Runbook

## Purpose
## Before The Interview
## Start Middleware
## Local Queue Demo
## Rabbit Queue Demo
## What To Say While Clicking
## Expected States
## Fallback Plan
## Shutdown
```

- [ ] **Step 2: Update runbook index**

Add `interview-demo.md` to `docs/runbooks/README.md`.

- [ ] **Step 3: Validate commands**

Run:

```powershell
docker compose -f infra/docker-compose.yml config
```

Expected: normalized compose configuration and exit code 0.

- [ ] **Step 4: Scan for incomplete markers**

Run:

```powershell
Select-String -Path docs\runbooks\interview-demo.md,docs\runbooks\README.md -Pattern 'T[B]D','TO[D]O','late[r] fill','placeholde[r]'
```

Expected: no output.

- [ ] **Step 5: Commit**

```powershell
git add docs/runbooks/interview-demo.md docs/runbooks/README.md
git commit -m "docs: add interview demo runbook"
```

---

## Task 3: Final Documentation Verification

**Files:**
- All files changed in Tasks 1-2.

- [ ] **Step 1: Check git whitespace**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 2: Verify documentation files exist**

Run:

```powershell
Test-Path docs\learning\phase-07-interview-demo-guide.md
Test-Path docs\runbooks\interview-demo.md
```

Expected:

```text
True
True
```

- [ ] **Step 3: Check working tree**

Run:

```powershell
git status --short --branch
```

Expected: branch `codex/phase-7-interview-demo-guide` with no uncommitted changes after commits.

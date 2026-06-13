# Phase 2 Learning Document Rewrite Design

## Goal

Rewrite `docs/learning/phase-02-intent-agent-project.md` as a beginner-friendly teaching document for a reader who has almost no backend, Java, Spring Boot, HTTP API, database, or testing background.

## Reader Model

The reader should not be expected to already understand Controller, Service, Repository, DTO, Entity, transaction, JSON, JPA, DAG, or integration testing. Each concept must be introduced in plain Chinese before it is used to explain the Phase 2 code.

## Teaching Approach

Use a repeated pattern:

1. Explain one small concept in everyday language.
2. Show where that concept appears in the Phase 2 codebase.
3. Walk through the data flow step by step.
4. Add a short checkpoint question or small exercise so the reader can test understanding.

The document should combine conceptual teaching with "follow the request" walkthroughs. It should not be a terse implementation summary.

## Structure

The rewritten document will keep the original required sections so it still satisfies the Phase 2 plan:

- 学习目标
- 业务背景
- 核心概念
- 设计原因
- 端到端流程
- 代码导读
- 边界条件
- 测试说明
- 排错手册
- 面试讲法

It may add extra learner-oriented sections such as "怎么使用这份文档", "先建立最小后端概念", and "学习路线" as long as the required sections remain present.

## Constraints

- Keep the work docs-only, except verification commands.
- Do not change backend behavior.
- Use clear UTF-8 Chinese text.
- Reference concrete files and commands.
- Include `cd backend-java` and `mvn test`.
- Avoid unresolved placeholder markers.
- Mention Phase 2 boundaries: no real LLM, no DAG execution runner, no SSE, no runtime HumanGate decision, no Artifact/Reflection/Lessons generation.

## Verification

After rewriting:

- Scan the document for placeholder markers.
- Confirm required headings exist.
- Run `git diff --check`.
- Run full backend `mvn test` before final merge.

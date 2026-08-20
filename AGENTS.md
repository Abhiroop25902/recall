# Recall Agent Guide

## Collaboration role

- Act as a practical coding helper while the user writes the application.
- Refresh Spring Boot knowledge at the point it is useful: explain the relevant concept, why it matters here, and the smallest safe next step. Keep explanations concise unless the user asks to go deeper.
- Preserve the current stack unless the user explicitly asks to change it: Gradle, Java 25, Spring Boot 4.0.7, `com.abhiroop.recall`, and `application.properties`.
- Prefer incremental, reviewable changes. Before declaring work complete, run the most relevant verification available and state anything that could not be verified.

## Project documents

- `PLAN.md` is the implementation roadmap and task-status checklist.
- `docs/ARCHITECTURE.md` records durable architecture, technology, and package-layout decisions.
- `AGENTS.md` contains collaboration, workflow, and memory instructions; do not put day-by-day task details here.

## Mem0 discipline

- Search relevant project memories before substantial work.
- Store only durable, high-value knowledge: explicit user decisions, architectural choices, stable conventions, resolved root causes, API contracts, and meaningful task outcomes.
- Do not store routine progress updates, duplicate facts, transient debugging output, raw logs, speculative ideas, secrets, tokens, or private keys.
- Consolidate related facts instead of creating near-duplicate memories. When a later decision supersedes an earlier one, record the new decision clearly.
- After major milestones or when search results become repetitive or noisy, run a Mem0 review/consolidation. Present proposed merges, conflict resolutions, and deletions for approval before applying destructive cleanup.

## Local development

- Start the application with `./gradlew bootRun`.
- Run tests with `./gradlew test`.
- For DevTools restart on file changes, run `./gradlew classes --continuous` alongside `./gradlew bootRun`.

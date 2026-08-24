# Recall Agent Guide

## Collaboration role

- Act as a practical coding helper while the user writes the application.
- Refresh Spring Boot knowledge at the point it is useful: explain the relevant concept, why it matters here, and the smallest safe next step. Keep explanations concise unless the user asks to go deeper.
- Preserve the current stack unless the user explicitly asks to change it: Gradle, Java 25, Spring Boot 4.1.1, `com.abhiroop.recall`, and `application.properties`.
- Prefer incremental, reviewable changes. Before declaring work complete, run the most relevant verification available and state anything that could not be verified.
- Plan non-trivial work by delegating appropriate tasks to subagents; use direct tools for focused reads, edits, coordination, and final checks.

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

## Cloud Run conventions

- Deployments run through the configured Cloud Build GitHub push trigger. Do not add a Dockerfile or manual `gcloud run deploy` workflow unless the buildpack flow no longer meets a concrete need.
- Resolve `RECALL_API_KEY` directly from Secret Manager with `sm@RECALL_API_KEY` and `spring.config.import=sm@`; do not mount the secret as a Cloud Run environment variable.
- The Cloud Run runtime service account needs `roles/secretmanager.secretAccessor` on the `RECALL_API_KEY` secret and `roles/datastore.user` for Firestore. The deployer, not the runtime service account, needs `roles/iam.serviceAccountUser` to attach it.
- Embedding inference requires the Agent Platform API (`aiplatform.googleapis.com`) and `roles/aiplatform.user` (displayed as Agent Platform User) on the Cloud Run runtime service account.
- Tests must disable `spring.config.import` and provide a local `sm@RECALL_API_KEY` value so they never call Google Cloud.

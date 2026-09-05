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

## Recall memory discipline

- Search relevant project memories with `getTopNClosest` before substantial work.
- Store only durable, high-value knowledge with `saveMemory`: explicit user decisions, architectural choices, stable conventions, resolved root causes, API contracts, and meaningful task outcomes.
- Do not store routine progress updates, duplicate facts, transient debugging output, raw logs, speculative ideas, secrets, tokens, or private keys.
- Retrieve 5-10 scoped comparable memories before saving. Consolidate only clear duplicates or contradictions; preserve separate records when uncertain.
- Use `getMemories` only for an explicit full-project audit. Responses contain `memories` and `nextCursor`. Read all pages before proposing cleanup: omit `cursor` initially, then pass the server-issued `nextCursor` unchanged as `cursor`. Stop when `nextCursor` is null; do not reconstruct it from memory timestamps. Present proposed merges, conflict resolutions, and deletions for approval before destructive cleanup.
- Derive `appId` from the current project: use the repository name for code projects or the folder name otherwise. Reuse it across agents and devices; never use a folder path, branch, session ID, task name, or random value.

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
- Scoped vector retrieval requires a Firestore composite index with ascending `appId` and a 1536-dimensional flat vector index on `embedding`.
- For live MCP tests, authenticate with the locally injected `$RECALL_API_KEY`, not a `gcloud` secret lookup; use unique temporary app IDs, verify cross-app isolation, then delete and verify cleanup of all test records.
- Keep embedding generation synchronous. The Hyderabad-to-Singapore custom-domain benchmark had 609 ms save p50, 733 ms save p95, 609 ms retrieval p50, and 917 ms retrieval p95; reconsider deferred embeddings only if real-use p95 exceeds 2 s or errors appear.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

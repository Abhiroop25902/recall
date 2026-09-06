# Recall: Developer Implementation Plan

This file tracks implementation work. The current architecture and package layout live
in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); agent collaboration and memory guidance live in [AGENTS.md](AGENTS.md).

Recall's application interface is a stateless Streamable HTTP MCP server for coding agents. There is no custom REST memory API;
the health endpoint is `/v1/health`, and the MCP endpoint is `/v1/mcp`.

## Progress

- [x] Initial project commit: `2535fa4` (`initial commit`)
- [x] Day 1: Project bootstrapping & local setup — implementation and runtime verification complete.
- [x] Day 2: Domain model & Firestore configuration — implementation and Firestore CRUD verification complete.
- [x] Day 3: Stateless MCP server & CRUD tools — implementation and Firestore-backed CRUD verification complete.
- [x] Day 4: Cloud Run deployment — Cloud Build GitHub trigger and deployed service verification complete.
- [x] Day 5: Firestore vector similarity search — scoped retrieval live verified; threshold filtering deferred.
- [x] Day 6: Gemini text embeddings client — save-time and query-time generation complete.
- [x] Day 7: Singapore regional migration & custom domain — Firestore, Cloud Build, and Cloud Run moved; live MCP, latency, custom-domain TLS, deployed-URL documentation, and Mumbai retirement complete.
- [x] Day 8: OpenCode proposed-save consolidation — OpenCode harness, pagination implementation, guidance, unit tests, MCP cursor behavior, and live pagination verification complete.
- [x] Day 9: OpenCode integration & MVP verification — existing single-user use confirms authenticated save, retrieval, and deletion work end-to-end; separate-device testing is not required.
- [x] Day 10: Google Cloud self-hosting setup guide complete.
- [ ] Day 11: Reliability and security remediation — decomposed into independently verifiable application, test, and script handoffs.

---

## Day 1: Project Bootstrapping & Local Setup

**Goal:** Run the Java 25 / Spring Boot 4 application locally.

- [x] **Task 1.1: Gradle project setup**
    - Use Gradle, Java 25, Spring Boot 4.1.1, Spring Web MVC, Lombok, and DevTools.
- [x] **Task 1.2: Application entrypoint & configuration**
    - `com.abhiroop.recall.RecallApplication` is annotated with `@SpringBootApplication`.
    - Use `src/main/resources/application.properties`; Spring Boot uses port 8080 by default.
- [x] **Task 1.3: Health endpoint**
    - `GET /v1/health` returns `{ "status": "UP", "service": "recall" }`.
- [x] **Verification**
  ```bash
  ./gradlew test
  ./gradlew bootRun
  curl http://localhost:8080/v1/health
  ```

---

## Day 2: Domain Model & Firestore Configuration

**Goal:** Connect to Cloud Firestore and implement basic CRUD persistence.

- [x] **Task 2.1: Add Firestore dependencies and typed configuration**
    - Add the Google Cloud libraries BOM and Firestore SDK to `build.gradle`.
    - Create `config/RecallProperties.java` using `@ConfigurationProperties(prefix = "recall")` for `gcpProjectId`.
    - Defer `firestoreCollection`, Gemini properties, and authentication properties until their respective tasks require them.
- [x] **Task 2.2: Define the `Memory` domain entity**
    - Include the Firestore document `id`, required `appId` as the folder namespace, `text`, `embedding` as a native
      Firestore vector, and `createdAt`.
    - Memory changes use delete-then-create; there is no in-place update or `updatedAt` field.
    - Add a unit test rejecting null or blank `appId` values.
- [x] **Task 2.3: Firestore client and repository**
    - Add `config/FirestoreConfig.java` and a `MemoryRepository` with a Firestore-backed implementation.
    - Support save, find by id, delete by id, and listing scoped by `appId`.
- [x] **Verification**
    - Saved a test memory document through the deployed MCP server, retrieved it by `appId`, deleted it, and confirmed
      Firestore persistence in the configured project.

---

## Day 3: Stateless MCP Server & CRUD Tools

**Goal:** Provide the first usable MCP interface for OpenCode.

- [x] **Task 3.1: MCP transport**
    - Add the Spring AI WebMVC MCP server starter.
    - Set `spring.ai.mcp.server.protocol=STATELESS`; use `/v1/mcp` as the MCP endpoint.
- [x] **Task 3.2: CRUD tools**
    - Expose `saveMemory`, `getMemories`, and `deleteMemory` as Spring AI `@Tool` methods.
    - Use DTO input for MCP tools; keep the Firestore `Memory` entity inside the service/repository boundary.
    - Back the tools with the Firestore repository and preserve delete-then-create memory changes.
    - Defer `search_memories` until vector search and embeddings are available.
- [x] **Verification**
    - [x] Complete an MCP handshake and discover the registered tools locally.
    - [x] Verify local `saveMemory` MCP wiring.
    - [x] Invoke all CRUD tools against the Firestore emulator or a configured project.

---

## Day 4: Cloud Run Deployment

**Goal:** Host the stateless MCP server for OpenCode.

- [x] **Task 4.1: Single-user API-key authentication**
    - Add application-level bearer authentication for this single-user deployment.
    - Use one shared key for the owner; defer customer accounts, key registries, and per-customer authorization.
    - Resolve `RECALL_API_KEY` directly from Secret Manager through `sm@`; never commit, log, or mount the key as an environment variable.
    - Require `Authorization: Bearer <RECALL_API_KEY>` for `/v1/mcp` and return `401` for missing or invalid keys.
    - Keep `GET /v1/health` available without an API key for health checks.
    - [x] Add tests for missing, invalid, and valid credentials before deployment.
- [x] **Task 4.2: Managed deployment**
    - Deploy through the configured Cloud Build GitHub push trigger; the Cloud Run Java buildpack builds and runs the application.
    - Store `RECALL_API_KEY` in Secret Manager and grant the Cloud Run runtime service account access to resolve it directly.
    - Use Cloud Run public access intentionally; the application-level API-key filter protects `/v1/mcp`.
    - Grant the runtime service account only the Firestore and Secret Manager permissions it needs.
- [x] **Verification**
    - [x] Confirm `GET /v1/health` is public at `https://recall-768264222351.asia-south1.run.app`.
    - [x] Confirm deployed `/v1/mcp` returns `401` for missing and invalid bearer keys.
    - [x] Complete an authenticated MCP handshake and CRUD tool calls against the deployed `/v1/mcp` endpoint.

---

## Day 5: Firestore Vector Similarity Search

**Goal:** Implement scoped vector similarity search using Firestore native vector queries.

Save-time embedding generation is complete as a prerequisite; query embedding generation is provided by Day 6.

- [x] **Task 5.1: Repository contract**
    - Add app-scoped memory counting and nearest-neighbor retrieval with a `VectorValue` query embedding and `topN` limit.
- [x] **Task 5.2: Firestore query**
    - Filter by `appId` before the Firestore nearest-neighbor query and rank by cosine distance.
    - Provision the required composite index: ascending `appId` plus a 1536-dimensional flat vector index on `embedding`.
- [x] **Task 5.3: Result mapping**
    - Project the document ID, app ID, text, and creation time; exclude stored embeddings from retrieval results.
- [ ] **Task 5.4: Threshold filtering**
    - Add an explicit similarity threshold when the retrieval consumer needs one.
- [x] **Unit verification**
    - Cover empty namespaces, bounds validation, query embedding normalization, and repository delegation in `MemoryServiceTest`.
- [x] **Live verification**
    - Saved five app-A records and a closer app-B control using `$RECALL_API_KEY`; scoped top-three retrieval returned relevant app-A records and excluded the app-B record.
    - Baseline read was 156 ms; median save was 539 ms, warm-save mean was 530 ms, and top-three retrieval was 534 ms.
    - Retain synchronous embedding generation; reconsider deferred embeddings only if live save latency materially exceeds this baseline.

---

## Day 6: Gemini Text Embeddings Client

**Goal:** Convert memory text and search queries into embeddings.

- [x] **Task 6.1: Spring AI Gemini embedding model**
    - Configure the Google GenAI embedding starter with the project, global location, model, and 1536 output dimensions.
- [x] **Task 6.2: Save-time embedding creation**
    - Generate and L2-normalize an embedding for each `saveMemory` request before persisting it as a Firestore `VectorValue`.
- [x] **Task 6.3: Search integration**
    - Generate and normalize a query embedding in `MemoryService.getTopNClosest(...)` before scoped vector retrieval.
- [x] **Verification**
    - Unit test embedding-to-Firestore-vector conversion during memory creation.
    - Unit test query embedding generation and vector retrieval delegation.
- [x] **Live verification**
    - Confirmed deployed `saveMemory` persists a Firestore vector; `getMemories` retrieved the test record by `appId` and `deleteMemory` removed it.

---

## Day 7: Singapore Regional Migration & Custom Domain

**Goal:** Move the empty Recall data and runtime to `asia-southeast1`, then serve it at `recall.abhiroop.dev`.

- [x] Confirm no clients can write to the existing Mumbai deployment during migration; the Mumbai Firestore database and Cloud Run service have been deleted.
- [x] Delete the empty Mumbai default Firestore Native database and recreate the default database in `asia-southeast1`.
- [x] Recreate the required Firestore index: ascending `appId` plus a 1536-dimensional flat vector index on `embedding`.
- [x] Update the external Cloud Build deployment configuration to deploy Recall to `asia-southeast1`.
- [x] Verify public health, MCP authentication, save, scoped vector search, cross-app isolation, and cleanup against the Singapore deployment.
  - Verified `https://recall-768264222351.asia-southeast1.run.app`: authenticated MCP initialization and tool discovery passed; five records in a temporary namespace returned a relevant top-three result while an identical sixth record in a separate namespace was excluded. All six test records were deleted and both namespaces were confirmed empty.
- [x] Verify live save/search latency; Vertex AI embeddings remain configured at `global`.
  - Custom-domain run: first save was 3.104 s; the following five saves averaged 917 ms (median 938 ms), and scoped top-three retrieval took 1.057 s. DNS resolution is cached and does not explain per-request latency; the measured round trip includes client network, Cloud Run, embedding, and Firestore work.
  - Follow-up warm benchmark: 30 saves had a 609 ms p50, 733 ms p95, and 891 ms p99; 30 scoped top-one retrievals had a 609 ms p50, 917 ms p95, and 1.003 s p99. All 66 calls succeeded, and all 36 temporary records were deleted and verified absent. Mean DNS lookup was 3 ms; mean TLS setup was 168-175 ms, while the remaining time to first byte includes Cloud Run, embedding, and Firestore work.
  - Repeat with `scripts/live-mcp-benchmark.sh`, which records client timing phases and cleans up only IDs created by its unique test namespaces.
- [x] Verify ownership of `abhiroop.dev`, map `recall.abhiroop.dev` to the Singapore Cloud Run service, and add the Cloud Run-provided DNS records.
- [x] Verify managed TLS and authenticated MCP access at `https://recall.abhiroop.dev/v1/mcp`.
- [x] Update deployed-URL documentation with `https://recall.abhiroop.dev/v1/mcp`; authenticated MCP initialization succeeded at the custom domain.
- [x] Retire the Mumbai Cloud Run service after the custom domain works reliably.

---

## Day 8: MCP Usage Guidance & Harness Consolidation

**Goal:** Publish a harness-neutral proposed-save contract through MCP, then verify it with OpenCode without adding a backend fact-extraction pipeline or periodic cleanup job.

- [x] **Task 8.1: MCP usage guidance**
    - Publish a harness-neutral "how to use Recall" contract through the MCP `initialize` instructions.
    - Require clients to select durable candidate facts, retrieve the scoped top 5-10 comparable memories with `getTopNClosest`, then choose ADD, NOOP, or delete-then-create replacement.
    - Treat only clear duplicates or contradictions as grounds for NOOP or replacement; preserve separate memories when uncertain and never save secrets or credentials.
    - Do not add a separate guidance tool, prompt, or resource unless client testing shows the initialization instructions are insufficient.
- [x] **Task 8.2: Existing MCP primitives**
    - Use the existing `saveMemory` and `deleteMemory` tools; do not add backend fact extraction, a Cloud Run Job, or periodic full-namespace cleanup.
- [x] **Task 8.3: OpenCode harness test**
    - Add a project-local OpenCode MCP configuration for the deployed endpoint without committing credentials.
    - Confirm OpenCode discovers the server guidance and applies the proposed-save contract.
- [x] **Task 8.4: Bounded memory listing**
    - Implementation, client guidance, unit tests, and local Spring AI tool callback roundtrip complete.
    - Local verification (2026-09-05): `./gradlew test` passed all 28 tests; `git diff --check` and `bash -n scripts/live-mcp-benchmark.sh` passed. Tests cover empty/short/full response pages, cursor validation, nanosecond-preserving conversion, and distinct mocked Firestore query stages for ordering/limit/startAfter. The application's actual Spring AI callback provider accepts omitted cursor, emits an ISO Instant with nine fractional digits, accepts the returned cursor unchanged, and emits a terminal null cursor. This is not a live MCP transport or Firestore integration test.
    - `getMemories` returns `GetMemoriesPage(memories, nextCursor)` with a fixed page size of 10 and deterministic ordering. Omit `cursor` initially, pass server-issued `nextCursor` unchanged, and stop on null; never reconstruct it from memory timestamps. Full pages issue a cursor, so exact multiples require a final empty request.
    - Reserve paginated listing for explicit full-project audits and cleanup; use `getTopNClosest` for routine retrieval and consolidation.
    - Updated tool description, `spring.ai.mcp.server.instructions`, `AGENTS.md`, and architecture guidance to the server-issued cursor contract. Ran `graphify update .` (AST-only); graphify refreshed five community names from hubs and recommends optional relabeling.
    - Deployed latency recheck (2026-09-05), using `scripts/live-mcp-benchmark.sh` and the injected API key: 30 saves mean 665 ms, p50 660 ms, p95 763 ms, p99 800 ms; 30 scoped top-one retrievals mean 677 ms, p50 671 ms, p95 815 ms, p99 865 ms. All 66 workload calls passed; retrieval isolation passed; all 36 generated records were deleted and both temporary namespaces verified empty. CSV: `/tmp/recall-benchmark-20260905T155355Z-33847.csv`.
    - Live pagination verification (2026-09-06), using `scripts/live-mcp-pagination-test.sh` with the injected API key and local ADC only for Firestore fixture creation: seeded 21 same-timestamp records with randomized IDs in a unique temporary namespace, then verified deployed MCP page objects, server-issued cursor relay, deterministic document-ID ordering and scope, `10/10/1`, exact-multiple `10/10/0`, and an empty namespace. All 21 owned fixture records were deleted through MCP and both temporary namespaces were verified empty through `getMemories`.
- [x] **Verification**
    - [x] An authenticated deployed MCP `initialize` response contains the published proposed-save guidance and deployed pagination behavior was verified under Task 8.4.
    - [x] From OpenCode, verified duplicate no-op, contradiction replacement, unrelated-memory addition, app isolation, and cleanup.

---

## Day 9: OpenCode Integration & MVP Verification

**Goal:** Connect coding agents to the deployed memory service.

- [x] **Task 9.1: MCP client configuration**
    - Document the deployed `/v1/mcp` URL, authentication setup, and proposed-save consolidation instruction without committing credentials.
- [x] **Task 9.2: End-to-end verification**
    - Accepted existing single-user OpenCode use of authenticated save, retrieval, and deletion as MVP verification; separate-device testing is not a requirement.

---

## Day 10: Google Cloud Self-Hosting Setup Guide

**Goal:** Document how to provision a new Google Cloud project for Recall.

- [x] **Task 10.1: Setup guide**
    - `README.md` covers project and billing setup, required APIs, Firestore Native mode, Secret Manager, IAM, Cloud Build, Cloud Run, OpenCode configuration, and MCP verification.
    - The guide specifies the vector index (`appId` ascending with a 1536-dimensional flat `embedding`) and ordered listing index (`appId`, `createdAt`, and document ID ascending).

---

## Day 11: Reliability and Security Remediation

**Goal:** Correct review findings before the next deployment, with cloud-free regression coverage.

**Execution ownership:** application, configuration, guidance, and Cloud Console changes are user-owned. Codex owns automated-test and live-script changes. Each Codex test handoff follows its corresponding user-owned application change.

- [ ] **Task 11.1a: Share the configured MCP endpoint matcher** *(user)*
    - Use one configured, context-aware matcher for the authentication filter and Spring Security authorization.
    - Preserve public health access; protect the configured MCP route for every HTTP method.
- [ ] **Task 11.1b: Create a cloud-free real-MCP test fixture** *(Codex; after Task 11.1a)*
    - Boot the actual Spring AI WebMVC MCP transport with local repository and embedding substitutes.
    - Exclude Secret Manager, Firestore, Google embedding, and ADC initialization.
- [ ] **Task 11.1c: Verify configured MCP route authentication** *(Codex; after Task 11.1b)*
    - Exercise a real MCP `initialize` request at the default endpoint, an overridden endpoint, and behind a context path.
    - Verify the active endpoint is challenged and a valid credential reaches the MCP transport; the stale default route must not become an unprotected MCP route.
- [ ] **Task 11.1d: Fail fast for invalid API-key configuration** *(user)*
    - Fail startup when `RECALL_API_KEY` is absent, blank, or whitespace-only.
    - Return `401` bearer authentication failures with `WWW-Authenticate: Bearer` without exposing credential details.
- [ ] **Task 11.1e: Verify API-key and bearer failure contracts** *(Codex; after Task 11.1d)*
    - Cover absent, blank, whitespace-only, malformed, invalid, and valid credentials against the real MCP route.

- [ ] **Task 11.2a: Validate required tool inputs before external work** *(user)*
    - Reject null or blank app IDs and save/query text before embedding, Firestore counting, listing, or vector queries.
    - Preserve the existing `topN` contract while rejecting invalid inputs before its zero-result shortcut.
    - Defer proactive size limits: do not add arbitrary character caps. Revisit only with a concrete token- or byte-based contract.
- [ ] **Task 11.2b: Prove invalid tool inputs do no external work** *(Codex; after Task 11.2a)*
    - Cover invalid save, list, and search inputs and assert zero embedding and repository interactions.
- [ ] **Task 11.2c: Make embedding normalization a strict boundary** *(user)*
    - Reject null, zero, non-finite, and non-1536-dimensional vectors; calculate the norm with a numerically safe accumulator.
- [ ] **Task 11.2d: Cover strict embedding normalization** *(Codex; after Task 11.2c)*
    - Migrate valid fixtures to 1536 dimensions and cover wrong-size, zero, null, NaN, infinity, and large finite vectors.

- [ ] **Task 11.3a: Publish recoverable replacement guidance** *(user)*
    - Require clients to save and verify a replacement before deleting an obsolete memory.
    - On an ambiguous save result, require scoped reconciliation and prohibit blind retry; stop for audit if reconciliation is inconclusive.
    - Do not add idempotency keys unless unattended automatic retries become a concrete requirement.
- [ ] **Task 11.3b: Verify replacement safety guidance and behavior** *(Codex; after Task 11.3a)*
    - Assert the MCP initialization guidance publishes the replacement and ambiguity rules.
    - Prove a failed replacement save leaves the prior record intact and a successful save returns its replacement ID before a separate delete.

- [ ] **Task 11.4a: Make every test context cloud-free** *(Codex; after Task 11.1b)*
    - Replace or isolate full application contexts so tests never initialize Secret Manager, Firestore, embedding clients, or ADC.
    - Verify `./gradlew test` with cloud credentials intentionally unavailable.
- [ ] **Task 11.4b: Cover a real MCP tool flow with local substitutes** *(Codex; after Task 11.4a)*
    - Verify actual MCP initialization, tool discovery, and one deterministic authenticated tool call without cloud services.
- [ ] **Task 11.4c: Cover Firestore repository failure paths** *(Codex)*
    - Test failed and interrupted Firestore futures through public repository methods, preserving causes and restoring the interrupt flag.
- [ ] **Task 11.4d: Verify the Cloud Build test gate** *(user; after code tests pass)*
    - Confirm in Cloud Console and a successful build log that `./gradlew test` or `./gradlew check` runs before buildpack deployment.
    - Record the trigger identity, command, ordering, and verification evidence.

- [ ] **Task 11.5a: Make live-script MCP assertions strict** *(Codex)*
    - Propagate `curl` failures explicitly; require JSON-RPC 2.0, no top-level error, a result, and `isError == false` for every successful MCP assertion.
    - Ensure every `jq` assertion exits nonzero when its predicate evaluates `false`; exercise missing `isError` and top-level error fixtures without a live deployment.
- [ ] **Task 11.5b: Reconcile live-script cleanup completely** *(Codex; after Task 11.5a)*
    - Reconcile every known ID and every page in each temporary namespace after ambiguous writes, then verify all records are absent.
    - Preserve the original exit status while making unresolved cleanup failures fail the script; remain compatible with macOS Bash 3.2.
- [ ] **Task 11.5c: Bind overridden pagination endpoints to an explicit Firestore project** *(Codex)*
    - Require `RECALL_GCP_PROJECT_ID` before any request when `RECALL_URL` is overridden, and print the selected endpoint and project.
- [ ] **Task 11.5d: Strengthen and label benchmark observations** *(Codex)*
    - Verify scoped retrieval in both primary and control namespaces with distinguishable fixtures, and assert no result crosses the requested app ID.
    - Label results as sequential client-observed end-to-end measurements, not a concurrency or load benchmark.
- [ ] **Verification**
    - Run `./gradlew test` with credentials intentionally unavailable.
    - Run `bash -n scripts/live-mcp-benchmark.sh scripts/live-mcp-pagination-test.sh` and exercise their negative assertion paths.
    - After deployment, verify default and overridden MCP endpoint authentication, successful replacement without data loss, and fixture cleanup.

## Completion criteria

The project is complete when the deployed service accepts authenticated MCP tool calls from OpenCode and has live-verified
save, scoped retrieval, and deletion behavior.

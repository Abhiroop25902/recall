# Recall: Developer Implementation Plan

This file tracks implementation work. The current architecture and package layout live
in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); agent collaboration and Mem0 guidance live in [AGENTS.md](AGENTS.md).

Recall's application interface is a stateless Streamable HTTP MCP server for OpenCode. There is no custom REST memory API;
the health endpoint is `/v1/health`, and the MCP endpoint is `/v1/mcp`.

## Progress

- [x] Initial project commit: `2535fa4` (`initial commit`)
- [x] Day 1: Project bootstrapping & local setup — implementation and runtime verification complete.
- [x] Day 2: Domain model & Firestore configuration — implementation and Firestore CRUD verification complete.
- [x] Day 3: Stateless MCP server & CRUD tools — implementation and Firestore-backed CRUD verification complete.
- [x] Day 4: Cloud Run deployment — Cloud Build GitHub trigger and deployed service verification complete.
- [x] Day 5: Firestore vector similarity search — scoped retrieval live verified; threshold filtering deferred.
- [x] Day 6: Gemini text embeddings client — save-time and query-time generation complete.
- [ ] Day 7: Singapore regional migration & custom domain — Firestore, Cloud Build, and Cloud Run moved; live MCP, latency, and custom-domain TLS verification complete. Pending deployed-URL documentation and Mumbai retirement.
- [ ] Day 8: Gemini fact extraction & deduplication
- [ ] Day 9: OpenCode integration & cross-device verification
- [ ] Day 10: Post-MVP Google Cloud setup guide

---

## Day 1: Project Bootstrapping & Local Setup

**Goal:** Run the Java 25 / Spring Boot 4 application locally.

- [x] **Task 1.1: Gradle project setup**
    - Use Gradle, Java 25, Spring Boot 4.0.7, Spring Web MVC, Lombok, and DevTools.
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

- [ ] Confirm no clients can write to the existing Mumbai deployment during migration.
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
- [ ] Update deployed-URL documentation and MCP client configuration.
- [ ] Retire the Mumbai Cloud Run service after the custom domain works reliably.

---

## Day 8: Gemini Fact Extraction & Deduplication

**Goal:** Extract atomic facts and resolve duplicates or contradictions.

- [ ] **Task 7.1: Extraction contract**
    - Add an `ExtractedFact` record and prompt template for category, text, and confidence.
- [ ] **Task 7.2: LLM extraction**
    - Implement structured JSON fact extraction in `GeminiClient`.
- [ ] **Task 7.3: Memory engine**
    - Search comparable memories, choose ADD, DELETE, or NOOP; replace stale memories with delete then create.
- [ ] **Verification**
    - Process a multi-turn conversation and confirm clean fact storage without duplicates.

---

## Day 9: OpenCode Integration & Cross-Device Verification

**Goal:** Connect coding agents to the deployed memory service.

- [ ] **Task 8.1: MCP client configuration**
    - Document the deployed `/v1/mcp` URL and authentication setup without committing credentials.
- [ ] **Task 8.2: End-to-end verification**
    - Store a memory from one client and retrieve it from another environment.

---

## Day 10: Post-MVP Google Cloud Setup Guide

**Goal:** After MVP completion, document how to provision a new Google Cloud project for Recall.

- [ ] **Task 9.1: Setup guide**
    - Cover project and billing setup, required APIs, Firestore Native mode, the composite vector index, Secret Manager, IAM, Cloud Build, Cloud Run, and MCP verification.
- [ ] **Verification**
    - Validate the guide by provisioning a separate project without committing credentials.

## Completion criteria

The project is complete when the deployed service accepts authenticated MCP tool calls from OpenCode, persists and searches
scoped memories, and passes end-to-end retrieval from a separate client.

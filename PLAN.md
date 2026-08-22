# Recall: Developer Implementation Plan

This file tracks implementation work. The current architecture and package layout live
in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); agent collaboration and Mem0 guidance live in [AGENTS.md](AGENTS.md).

Recall's application interface is a stateless Streamable HTTP MCP server for OpenCode. There is no custom REST memory API;
the existing health endpoint remains for service health checks.

## Progress

- [x] Initial project commit: `2535fa4` (`initial commit`)
- [x] Day 1: Project bootstrapping & local setup — implementation and runtime verification complete.
- [ ] Day 2: Domain model & Firestore configuration — implementation complete; Firestore CRUD verification pending.
- [ ] Day 3: Stateless MCP server & CRUD tools — implementation complete; Firestore-backed CRUD verification pending.
- [ ] Day 4: Cloud Run deployment
- [ ] Day 5: Firestore vector similarity search
- [ ] Day 6: Gemini text embeddings client
- [ ] Day 7: Gemini fact extraction & deduplication
- [ ] Day 8: OpenCode integration & cross-device verification

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
    - Defer `firestoreCollection`, Gemini properties, and `authToken` until their respective tasks require them.
- [x] **Task 2.2: Define the `Memory` domain entity**
    - Include the Firestore document `id`, required `appId` as the folder namespace, `text`, `embedding` as a native
      Firestore vector, and `createdAt`.
    - Memory changes use delete-then-create; there is no in-place update or `updatedAt` field.
    - Add a unit test rejecting null or blank `appId` values.
- [x] **Task 2.3: Firestore client and repository**
    - Add `config/FirestoreConfig.java` and a `MemoryRepository` with a Firestore-backed implementation.
    - Support save, find by id, delete by id, and listing scoped by `appId`.
- [ ] **Verification**
    - Use the Firestore emulator or a configured project to save a test memory document, read it back, delete it, and
      confirm folder-scoped listing.

---

## Day 3: Stateless MCP Server & CRUD Tools

**Goal:** Provide the first usable MCP interface for OpenCode.

- [x] **Task 3.1: MCP transport**
    - Add the Spring AI WebMVC MCP server starter.
    - Set `spring.ai.mcp.server.protocol=STATELESS`; use `/api/mcp` as the MCP endpoint.
- [x] **Task 3.2: CRUD tools**
    - Expose `saveMemory`, `getMemories`, and `deleteMemory` as Spring AI `@Tool` methods.
    - Use DTO input for MCP tools; keep the Firestore `Memory` entity inside the service/repository boundary.
    - Back the tools with the Firestore repository and preserve delete-then-create memory changes.
    - Defer `search_memories` until vector search and embeddings are available.
- [ ] **Verification**
    - [x] Complete an MCP handshake and discover the registered tools locally.
    - [x] Invoke `saveMemory` through MCP using `ConsoleTestMemoryRepository`.
    - [ ] Invoke all CRUD tools against the Firestore emulator or a configured project.

---

## Day 4: Cloud Run Deployment

**Goal:** Host the stateless MCP server for OpenCode.

- [ ] **Task 4.1: Dockerfile**
    - Build with Gradle and run the executable Spring Boot jar on port 8080.
- [ ] **Task 4.2: Deployment**
    - Add a `gcloud run deploy` workflow with the Google Cloud project and Firestore access supplied through configuration.
    - Protect `/mcp` with Cloud Run IAM or a configured bearer token; do not deploy an unauthenticated memory service.
- [ ] **Verification**
    - Confirm the health endpoint and complete the MCP handshake against the deployed `/mcp` endpoint.

---

## Day 5: Firestore Vector Similarity Search

**Goal:** Implement scoped vector similarity search using Firestore native vector queries.

- [ ] **Task 5.1: Repository contract**
    - Add `searchSimilar(String appId, List<Double> queryVector, int topK, double threshold)`.
- [ ] **Task 5.2: Firestore query**
    - Use the `appId` filter before the nearest-neighbor query and rank by cosine distance.
- [ ] **Task 5.3: Result mapping**
    - Add `MemorySearchResult` and filter results below the requested threshold.
- [ ] **Verification**
    - Query with a dummy 768-dimensional vector and confirm scoped, ranked results.

---

## Day 6: Gemini Text Embeddings Client

**Goal:** Convert memory text and search queries into embeddings.

- [ ] **Task 6.1: Gemini client**
    - Add `llm/GeminiClient.java` using Spring `RestClient` or the Google GenAI SDK.
- [ ] **Task 6.2: Embedding service**
    - Add `embedText(String text)` and `batchEmbedText(List<String> texts)`.
- [ ] **Task 6.3: Search integration**
    - Combine embedding generation and vector search in `MemoryService.search(...)`.
- [ ] **Verification**
    - Confirm an embedding request returns the expected vector dimensionality for the selected model.

---

## Day 7: Gemini Fact Extraction & Deduplication

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

## Day 8: OpenCode Integration & Cross-Device Verification

**Goal:** Connect coding agents to the deployed memory service.

- [ ] **Task 8.1: MCP client configuration**
    - Document the deployed MCP URL and authentication setup without committing credentials.
- [ ] **Task 8.2: End-to-end verification**
    - Store a memory from one client and retrieve it from another environment.

## Completion criteria

The project is complete when the deployed service accepts authenticated MCP tool calls from OpenCode, persists and searches
scoped memories, and passes end-to-end retrieval from a separate client.

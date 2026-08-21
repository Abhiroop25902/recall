# Recall: Developer Implementation Plan

This file tracks implementation work. The current architecture and package layout live
in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); agent collaboration and Mem0 guidance live in [AGENTS.md](AGENTS.md).

## Progress

- [x] Initial project commit: `2535fa4` (`initial commit`)
- [x] Day 1: Project bootstrapping & local setup — implementation and runtime verification complete.
- [ ] Day 2: Domain model & Firestore configuration — implementation complete; Firestore CRUD verification pending.
- [ ] Day 3: Firestore vector similarity search
- [ ] Day 4: Gemini text embeddings client
- [ ] Day 5: Gemini fact extraction & deduplication
- [ ] Day 6: REST API & authentication
- [ ] Day 7: MCP server
- [ ] Day 8: Dockerfile & Cloud Run deployment
- [ ] Day 9: IDE integration & cross-device verification

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

## Day 3: Firestore Vector Similarity Search

**Goal:** Implement scoped vector similarity search using Firestore native vector queries.

- [ ] **Task 3.1: Repository contract**
    - Add `searchSimilar(String appId, List<Double> queryVector, int topK, double threshold)`.
- [ ] **Task 3.2: Firestore query**
    - Use the `appId` filter before the nearest-neighbor query and rank by cosine distance.
- [ ] **Task 3.3: Result mapping**
    - Add `MemorySearchResult` and filter results below the requested threshold.
- [ ] **Verification**
    - Query with a dummy 768-dimensional vector and confirm scoped, ranked results.

---

## Day 4: Gemini Text Embeddings Client

**Goal:** Convert memory text and search queries into embeddings.

- [ ] **Task 4.1: Gemini client**
    - Add `llm/GeminiClient.java` using Spring `RestClient` or the Google GenAI SDK.
- [ ] **Task 4.2: Embedding service**
    - Add `embedText(String text)` and `batchEmbedText(List<String> texts)`.
- [ ] **Task 4.3: Search integration**
    - Combine embedding generation and vector search in `MemoryService.search(...)`.
- [ ] **Verification**
    - Confirm an embedding request returns the expected vector dimensionality for the selected model.

---

## Day 5: Gemini Fact Extraction & Deduplication

**Goal:** Extract atomic facts and resolve duplicates or contradictions.

- [ ] **Task 5.1: Extraction contract**
    - Add an `ExtractedFact` record and prompt template for category, text, and confidence.
- [ ] **Task 5.2: LLM extraction**
    - Implement structured JSON fact extraction in `GeminiClient`.
- [ ] **Task 5.3: Memory engine**
    - Search comparable memories, choose ADD, DELETE, or NOOP; replace stale memories with delete then create.
- [ ] **Verification**
    - Process a multi-turn conversation and confirm clean fact storage without duplicates.

---

## Day 6: REST API & Authentication

**Goal:** Expose protected `/v1/memories` endpoints.

- [ ] **Task 6.1: Request and response DTOs**
    - Add request types for adding and searching memories, plus a `MemoryResponse`.
- [ ] **Task 6.2: Memory controller**
    - Implement add, search, get by id, delete by id, and scoped delete endpoints.
- [ ] **Task 6.3: Authentication**
    - Add an authentication filter for the configured token; design Cloud Run IAM integration separately from
      local-token validation.
- [ ] **Verification**
    - Exercise add and search endpoints with authenticated requests.

---

## Day 7: MCP Server

**Goal:** Provide MCP tools for IDE agents.

- [ ] **Task 7.1: MCP transport**
    - Implement the selected MCP transport and message endpoint.
- [ ] **Task 7.2: Tool handlers**
    - Implement `add_memory`, `search_memories`, `get_memories`, and `delete_memory` through `MemoryService`.
- [ ] **Task 7.3: Tool schemas**
    - Register schemas compatible with the intended client contract.
- [ ] **Verification**
    - Complete an MCP handshake and invoke each tool through a local client.

---

## Day 8: Dockerfile & Cloud Run Deployment

**Goal:** Package the Gradle application and deploy it to Cloud Run.

- [ ] **Task 8.1: Dockerfile**
    - Build with Gradle and run the executable Spring Boot jar on port 8080.
- [ ] **Task 8.2: Deployment script**
    - Add a `gcloud run deploy` workflow with non-secret configuration supplied through environment variables or a
      managed secret store.
- [ ] **Task 8.3: Cloud Run verification**
    - Confirm the deployed health endpoint and scale-to-zero behavior.

---

## Day 9: IDE Integration & Cross-Device Verification

**Goal:** Connect coding agents to the deployed memory service.

- [ ] **Task 9.1: MCP client configuration**
    - Document the deployed MCP URL and authentication setup without committing credentials.
- [ ] **Task 9.2: End-to-end verification**
    - Store a memory from one client and retrieve it from another environment.

## Completion criteria

The project is complete when the deployed service accepts authenticated memory operations, persists and searches scoped
memories, exposes compatible MCP tools, and passes end-to-end retrieval from a separate client.

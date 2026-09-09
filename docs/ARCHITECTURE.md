# Recall Architecture

## Current baseline

- Build: Gradle
- Language: Java 25
- Framework: Spring Boot 4.1.1
- Base package: `com.abhiroop.recall`
- Configuration: `application.properties`
- Local server: port 8080 by default
- Development reload: Spring Boot DevTools with continuous Gradle compilation
- Running the application requires Application Default Credentials and access to its configured Google Cloud project.

## HTTP surface

- `GET /v1/health` returns the service health response.
- `/v1/mcp` is the stateless Streamable HTTP MCP endpoint that exposes memory tools.
- The deployed MCP endpoint is `https://recall.abhiroop.dev/v1/mcp`.
- Memory operations currently use MCP tools; there is no custom REST memory API.
- MCP `initialize` responses publish harness-neutral guidance for using the memory tools.
- The `/v1` URL namespace is independent of the MCP protocol version configured by
  `spring.ai.mcp.server.version`.

## Current deployment

- Firestore runs in Standard edition, Native mode, in `asia-southeast1`.
- Cloud Run runs in `asia-southeast1` and uses an HTTP startup probe at `/v1/health`.
- The owner deployment is served at `https://recall.abhiroop.dev`; self-hosted deployments use their generated Cloud Run URL unless they independently configure a custom domain.

## Product target

Recall is a cloud-hosted memory service for AI agents working on coding and non-coding projects. It exposes a versioned health endpoint and an MCP-compatible server, with persistent memory backed by Google Cloud Firestore. Gemini/Vertex AI generates normalized embeddings for saved memories and vector-search queries. Recall provides storage, scoped retrieval, save, and delete primitives; each client harness decides which durable facts to save and resolves duplicates or contradictions before writing. Cloud Run is the deployment target.

## Deployment security

- The initial deployment is single-user and uses one `RECALL_API_KEY` stored in Secret Manager.
- Spring Cloud GCP resolves the key directly using `sm@RECALL_API_KEY` and the Cloud Run service identity; the key must not be committed to configuration, logs, or environment variables.
- The runtime service account has Secret Manager Secret Accessor on that secret and Cloud Datastore User for Firestore.
- Embedding inference requires the Agent Platform API and Agent Platform User (`roles/aiplatform.user`) on the runtime service account.
- `/v1/mcp` requires `Authorization: Bearer <RECALL_API_KEY>`.
- `/v1/health` remains unauthenticated for health checks.
- Cloud Run public access is intentional for remote MCP connectivity; application-level authentication protects memory operations.

## Test isolation

- Spring application-context tests use a shared cloud-free MCP configuration with local `MemoryRepository` and `EmbeddingModel` substitutes.
- The fixture clears Secret Manager import, disables Spring Cloud GCP auto-configuration, and excludes Google GenAI embedding auto-configuration; tests assert that no credentials provider, Firestore, or Google embedding-model beans are present.

## Memory model

Each Firestore document represents one memory in the `memories/{id}` collection. The required `appId` identifies a stable
project namespace: clients use the repository name for code projects or the folder name for other projects, consistently across agents and
devices. A memory contains its text, a 1536-dimensional L2-normalized Firestore `VectorValue` embedding generated at creation, and its
creation timestamp. Changes are handled by deleting the old document and creating a replacement; in-place updates and `updatedAt` are
intentionally not supported. Repository saves are create-only and cannot overwrite an existing ID.

`getTopNClosest(appId, text, topN)` is the MCP retrieval tool. It returns an empty result for `topN == 0`, rejects
values outside `0..1000`, and checks the app namespace before paying for query embedding inference. For non-empty
namespaces, the service obtains a provider-normalized query embedding and queries Firestore with an `appId` equality filter
and cosine nearest-neighbor search. Retrieval projects the document ID, app ID, text, and creation time, excluding the
stored embedding from MCP responses.

All memory tool inputs are validated before external work: saving requires nonblank `appId` and text, listing requires
a nonblank `appId`, and retrieval requires nonblank `appId` and text before its `topN == 0` shortcut, Firestore count,
or embedding inference. The service deliberately has no proactive character limit until a concrete token- or byte-based
contract requires one.

`getMemories(appId, cursor)` returns `GetMemoriesPage(memories, nextCursor)` with at most 10 memories and is reserved
for explicit full-project audits. Results are ordered ascending by `createdAt`, then document ID. Omit `cursor` for
the first page; pass the server-issued `nextCursor` unchanged as `cursor` for each subsequent page. Do not reconstruct
the cursor from memory timestamps. A supplied cursor requires an ISO-8601 `afterCreatedAt` Instant and a nonblank `afterId`.
The service issues a cursor from the last record of a full page using `Timestamp.toSqlTimestamp().toInstant()` and
converts it back without losing nanoseconds; the repository applies exclusive `startAfter(timestamp, id)`.
Stop when `nextCursor` is null (empty or short page); an exact multiple of 10 requires a final empty-page request.
Read all pages before proposing audit cleanup. Pagination is not a snapshot: concurrent writes and deletes may affect results.
The ordered query requires a suitable Firestore index for the `appId` filter and `createdAt`/document-ID ordering.
Live verification on 2026-09-06 seeded 21 records with an identical timestamp and randomized document IDs in a temporary namespace, then confirmed deployed MCP page objects, deterministic ordering, cursor relay, `10/10/1`, exact-multiple `10/10/0`, and cleanup through the tool. This confirms the deployed index and page-boundary behavior for that controlled workload.

The deployment requires two Firestore indexes: a vector index with `appId` ascending and a 1536-dimensional flat `embedding`, and an ordered listing index with `appId`, `createdAt`, and document ID ascending.

MCP `initialize` instructions define the proposed-save contract for every client: select only durable candidate facts,
retrieve the top 5-10 scoped candidates, then add the memory or remove redundant duplicates while retaining one canonical
memory. For a clearly stale memory, save the replacement, confirm its returned ID, then separately delete the obsolete
record. Reconcile an ambiguous save within the same app ID rather than blindly retrying; stop for audit if reconciliation
is inconclusive. Preserve separate memories when uncertain and never store secrets or credentials. Recall deliberately does
not run a backend fact-extraction pipeline, scheduled Cloud Run Job, or periodic full-namespace consolidation pass. A
client-specific skill or prompt is optional reinforcement, not part of this server contract.

## Planned module layout

```text
src/main/java/com/abhiroop/recall/
├── RecallApplication.java
├── config/       # Configuration properties, Firestore, security
├── controller/   # HTTP endpoints
├── entity/       # Firestore-backed domain records
├── llm/          # Gemini integration and prompts
├── mcp/          # MCP transport and tool handlers
├── model/        # Request and response DTOs
├── repository/   # Firestore persistence and vector search
└── service/      # Application workflows and memory engine
```

The implementation sequence and acceptance checks live in [PLAN.md](../PLAN.md).

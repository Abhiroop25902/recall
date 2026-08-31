# Recall Architecture

## Current baseline

- Build: Gradle
- Language: Java 25
- Framework: Spring Boot 4.0.7
- Base package: `com.abhiroop.recall`
- Configuration: `application.properties`
- Local server: port 8080 by default
- Development reload: Spring Boot DevTools with continuous Gradle compilation

## HTTP surface

- `GET /v1/health` returns the service health response.
- `/v1/mcp` is the stateless Streamable HTTP MCP endpoint that exposes memory tools.
- The deployed MCP endpoint is `https://recall.abhiroop.dev/v1/mcp`.
- Memory operations currently use MCP tools; there is no custom REST memory API.
- The `/v1` URL namespace is independent of the MCP protocol version configured by
  `spring.ai.mcp.server.version`.

## Product target

Recall is a cloud-hosted memory service for coding agents. It exposes a versioned health endpoint and an MCP-compatible server, with persistent memory backed by Google Cloud Firestore. Gemini/Vertex AI generates normalized embeddings for saved memories and vector-search queries; fact extraction remains pending. Cloud Run is the deployment target.

## Deployment security

- The initial deployment is single-user and uses one `RECALL_API_KEY` stored in Secret Manager.
- Spring Cloud GCP resolves the key directly using `sm@RECALL_API_KEY` and the Cloud Run service identity; the key must not be committed to configuration, logs, or environment variables.
- The runtime service account has Secret Manager Secret Accessor on that secret and Cloud Datastore User for Firestore.
- `/v1/mcp` requires `Authorization: Bearer <RECALL_API_KEY>`.
- `/v1/health` remains unauthenticated for health checks.
- Cloud Run public access is intentional for remote MCP connectivity; application-level authentication protects memory operations.

## Memory model

Each Firestore document represents one memory in the `memories/{id}` collection. The `appId` field is required and
identifies the folder namespace. A memory contains its text, a 1536-dimensional L2-normalized Firestore `VectorValue` embedding generated
at creation, and its creation timestamp. Changes are handled by deleting the old document and creating a replacement; in-place updates and
`updatedAt` are intentionally not supported. Repository saves are create-only and cannot overwrite an existing ID.

`getTopNClosest(appId, text, topN)` is the MCP retrieval tool. It returns an empty result for `topN == 0`, rejects
values outside `0..1000`, and checks the app namespace before paying for query embedding inference. For non-empty
namespaces, the service creates an L2-normalized query embedding and queries Firestore with an `appId` equality filter
and cosine nearest-neighbor search. Retrieval projects the document ID, app ID, text, and creation time, excluding the
stored embedding from MCP responses.

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

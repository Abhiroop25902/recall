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
- Memory operations currently use MCP tools; there is no custom REST memory API.
- The `/v1` URL namespace is independent of the MCP protocol version configured by
  `spring.ai.mcp.server.version`.

## Product target

Recall is a cloud-hosted memory service for coding agents. It exposes a versioned health endpoint and an MCP-compatible server, with persistent memory backed by Google Cloud Firestore and vector search. Gemini/Vertex AI will provide embeddings and fact extraction; Cloud Run is the deployment target.

## Deployment security

- The initial deployment is single-user and uses one externally supplied `RECALL_API_KEY`.
- Cloud Run will inject the key from Secret Manager; the key must not be committed to configuration or logs.
- `/v1/mcp` will require `Authorization: Bearer <RECALL_API_KEY>` before Cloud Run deployment.
- `/v1/health` remains unauthenticated for health checks.
- Cloud Run public access is intentional for remote MCP connectivity; application-level authentication protects memory operations.

## Memory model

Each Firestore document represents one memory in the `memories/{id}` collection. The `appId` field is required and
identifies the folder namespace. A memory contains its text, a Firestore `VectorValue` embedding, and its creation
timestamp. Changes are handled by deleting the old document and creating a replacement; in-place updates and
`updatedAt` are intentionally not supported. Repository saves are create-only and cannot overwrite an existing ID.

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

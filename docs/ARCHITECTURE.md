# Recall Architecture

## Current baseline

- Build: Gradle
- Language: Java 25
- Framework: Spring Boot 4.0.7
- Base package: `com.abhiroop.recall`
- Configuration: `application.properties`
- Local server: port 8080 by default
- Development reload: Spring Boot DevTools with continuous Gradle compilation

## Product target

Recall is a cloud-hosted memory service for coding agents. It will expose REST endpoints under `/v1`, an MCP-compatible server, and persistent memory backed by Google Cloud Firestore with vector search. Gemini/Vertex AI will provide embeddings and fact extraction; Cloud Run is the deployment target.

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
├── llm/          # Gemini integration and prompts
├── mcp/          # MCP transport and tool handlers
├── model/        # Domain records and DTOs
├── repository/   # Firestore persistence and vector search
└── service/      # Application workflows and memory engine
```

The implementation sequence and acceptance checks live in [PLAN.md](../PLAN.md).

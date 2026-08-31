# Recall

Recall is a personal, cloud-hosted memory service for coding agents.

## Project scope

Recall is a hobby project designed for personal, single-user use. It requires you to provision and manage your own cloud infrastructure and associated costs.

Recall is inspired by my experience using Mem0. Mem0 is a great product, and the fact that I want to build a focused project with a similar goal is a testament to how valuable I find it.

If you prefer a managed memory service with minimal setup and customer support, consider using Mem0 instead.

## Why I am building this

I use Mem0 to give coding agents persistent context, but the free tier is too limited for regular use. The Mem0 dashboard currently shows a hobby plan with limited retrieval capacity, while the paid plan is roughly **₹2,000 per month** in my local estimate.

That made me wonder:

> If I build my own focused version on Google Cloud, can the monthly cost be dramatically lower than ₹2,000 while still giving me the memory workflow I actually need?

I have a good feeling that the answer is yes. Recall is the experiment: build only the core memory capabilities, use serverless infrastructure, and measure the real cost instead of paying for a broad hosted product before I need it.

## What Recall is intended to provide

- Persistent memories scoped by application (`appId`) in a single-user deployment.
- Semantic search over stored memories using vector embeddings.
- MCP-guided proposed-save consolidation: clients handle duplicates and contradictions using scoped semantic retrieval.
- `GET /v1/health` for service health checks.
- MCP-compatible memory tools at `/v1/mcp`.
- Google Cloud Firestore persistence and Google Cloud Run deployment.

## Current development baseline

- Gradle
- Java 25
- Spring Boot 4.1.1
- Base package: `com.abhiroop.recall`
- Configuration: `application.properties`

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the durable architecture and [PLAN.md](PLAN.md) for the implementation roadmap.

## HTTP endpoints

The local service exposes:

- Health: `http://localhost:8080/v1/health`
- MCP: `http://localhost:8080/v1/mcp`
- Deployed MCP: `https://recall.abhiroop.dev/v1/mcp`

Memory operations are currently exposed through MCP tools rather than custom REST endpoints.
Day 8 will publish harness-neutral proposed-save guidance through MCP initialization; client-specific skills are optional reinforcement.

Cloud Run uses one Secret Manager-backed `RECALL_API_KEY` and requires this header for `/v1/mcp`:

```http
Authorization: Bearer <RECALL_API_KEY>
```

Do not commit the key or place it in a URL.

## Run locally

Start the application:

```bash
./gradlew bootRun
```

In another terminal, enable compile-on-save support for DevTools:

```bash
./gradlew classes --continuous
```

The health endpoint is available at:

```text
http://localhost:8080/v1/health
```

Run tests with:

```bash
./gradlew test
```

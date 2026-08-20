# Recall

Recall is a personal, cloud-hosted memory service for coding agents.

## Why I am building this

I use Mem0 to give coding agents persistent context, but the free tier is too limited for regular use. The Mem0 dashboard currently shows a hobby plan with limited retrieval capacity, while the paid plan is roughly **₹2,000 per month** in my local estimate.

That made me wonder:

> If I build my own focused version on Google Cloud, can the monthly cost be dramatically lower than ₹2,000 while still giving me the memory workflow I actually need?

I have a good feeling that the answer is yes. Recall is the experiment: build only the core memory capabilities, use serverless infrastructure, and measure the real cost instead of paying for a broad hosted product before I need it.

## What Recall is intended to provide

- Persistent memories scoped by user and application.
- Semantic search over stored memories using vector embeddings.
- Fact extraction and duplicate/contradiction handling.
- REST endpoints under `/v1`.
- MCP-compatible tools for coding agents.
- Google Cloud Firestore persistence and Google Cloud Run deployment.

## Current development baseline

- Gradle
- Java 25
- Spring Boot 4.0.7
- Base package: `com.abhiroop.recall`
- Configuration: `application.properties`

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the durable architecture and [PLAN.md](PLAN.md) for the implementation roadmap.

## Run locally

Start the application:

```bash
./gradlew bootRun
```

In another terminal, enable compile-on-save support for DevTools:

```bash
./gradlew classes --continuous
```

The initial health endpoint is available at:

```text
http://localhost:8080/v1/health
```

Run tests with:

```bash
./gradlew test
```

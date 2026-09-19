package com.abhiroop.recall.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in deployed retrieval-quality evaluation. Run with {@code ./gradlew liveIntegrationTest};
 * it uses fresh, UUID-scoped production namespaces and cleans up only memories it created.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("live")
class LiveMcpRetrievalEvaluationIntegrationTest {

    private static final String DEFAULT_URL = "https://recall.abhiroop.dev/v1/mcp";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final List<CreatedMemory> createdMemories = new ArrayList<>();
    private final Map<String, CreatedMemory> fixtureMemories = new HashMap<>();
    private String apiKey;
    private URI endpoint;
    private String primaryAppId;
    private String controlAppId;
    private String runId;
    private int requestId;

    @BeforeAll
    void configureLiveDependencies() {
        apiKey = requiredEnvironment();
        endpoint = URI.create(System.getenv().getOrDefault("RECALL_URL", DEFAULT_URL));
        runId = UUID.randomUUID().toString();
        primaryAppId = "recall-retrieval-eval-" + runId + "-primary";
        controlAppId = "recall-retrieval-eval-" + runId + "-control";

        System.out.printf(
                "Live retrieval evaluation endpoint=%s primaryAppId=%s controlAppId=%s%n",
                endpoint,
                primaryAppId,
                controlAppId
        );
    }

    @AfterAll
    void cleanUpFixtures() throws Exception {
        Exception cleanupFailure = null;
        for (CreatedMemory memory : List.copyOf(createdMemories)) {
            try {
                deleteMemory(memory.id());
                createdMemories.remove(memory);
            } catch (Exception exception) {
                cleanupFailure = addCleanupFailure(cleanupFailure, exception);
            }
        }

        for (String appId : List.of(primaryAppId, controlAppId)) {
            try {
                assertNamespaceEmpty(appId);
            } catch (Exception exception) {
                cleanupFailure = addCleanupFailure(cleanupFailure, exception);
            }
        }

        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    @Test
    void evaluatesRepresentativeProductionRetrievalCases() throws Exception {
        EvaluationDataset dataset = loadDataset();
        seedFixtures(dataset.fixtures());

        int casesWithExpectedMatches = 0;
        int hitAtOne = 0;
        int hitAtK = 0;
        double reciprocalRankTotal = 0;

        for (EvaluationCase evaluationCase : dataset.cases()) {
            List<JsonNode> results = getTopNClosest(appIdFor(evaluationCase.namespace()), evaluationCase.query(), evaluationCase.topN());
            assertTrue(
                    results.stream().allMatch(memory -> appIdFor(evaluationCase.namespace()).equals(memory.path("appId").asText())),
                    () -> evaluationCase.name() + " crossed the requested app namespace"
            );

            Set<String> resultIds = results.stream().map(memory -> memory.path("id").asText()).collect(java.util.stream.Collectors.toSet());
            Set<String> expectedIds = fixtureIds(evaluationCase.mustContainFixtureKeys());
            Set<String> foreignNamespaceIds = fixtureIdsForNamespace(oppositeNamespace(evaluationCase.namespace()));
            assertTrue(
                    java.util.Collections.disjoint(resultIds, foreignNamespaceIds),
                    () -> evaluationCase.name() + " returned a fixture from the other namespace"
            );

            printObservedRanking(evaluationCase, results);
            if (expectedIds.isEmpty()) {
                continue;
            }

            casesWithExpectedMatches++;
            int firstRelevantRank = firstRelevantRank(results, expectedIds);
            assertTrue(firstRelevantRank > 0, () -> evaluationCase.name() + " missed expected fixtures " + evaluationCase.mustContainFixtureKeys());
            hitAtK++;
            reciprocalRankTotal += 1.0 / firstRelevantRank;
            if (firstRelevantRank == 1) {
                hitAtOne++;
            }
        }

        System.out.printf(
                "Retrieval evaluation summary: cases=%d expected-match-cases=%d hit@1=%d/%d hit@k=%d/%d MRR=%.3f%n",
                dataset.cases().size(), casesWithExpectedMatches, hitAtOne, casesWithExpectedMatches,
                hitAtK, casesWithExpectedMatches, reciprocalRankTotal / casesWithExpectedMatches
        );
        assertTrue(casesWithExpectedMatches > 0, "dataset must contain cases with expected relevant fixtures");
    }

    private EvaluationDataset loadDataset() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/retrieval-evaluation-cases.json")) {
            assertNotNull(input, "retrieval evaluation dataset must be on the integration-test classpath");
            return OBJECT_MAPPER.readValue(input, EvaluationDataset.class);
        }
    }

    private void seedFixtures(List<Fixture> fixtures) throws Exception {
        for (Fixture fixture : fixtures) {
            String appId = appIdFor(fixture.namespace());
            JsonNode saved = callTool("saveMemory", OBJECT_MAPPER.createObjectNode()
                    .put("appId", appId)
                    .put("text", fixture.text() + " Evaluation run " + runId + "."));
            JsonNode memory = OBJECT_MAPPER.readTree(saved.at("/content/0/text").asText());
            CreatedMemory created = new CreatedMemory(fixture.key(), fixture.namespace(), memory.path("id").asText());
            assertFalse(created.id().isBlank(), "saveMemory must return an ID");
            createdMemories.add(created);
            fixtureMemories.put(fixture.key(), created);
        }
    }

    private List<JsonNode> getTopNClosest(String appId, String text, int topN) throws Exception {
        JsonNode result = callTool("getTopNClosest", OBJECT_MAPPER.createObjectNode()
                .put("appId", appId)
                .put("text", text)
                .put("topN", topN));
        JsonNode memories = OBJECT_MAPPER.readTree(result.at("/content/0/text").asText());
        assertTrue(memories.isArray(), "getTopNClosest must return an array");
        List<JsonNode> results = new ArrayList<>();
        memories.forEach(results::add);
        return results;
    }

    private void assertNamespaceEmpty(String appId) throws Exception {
        JsonNode result = callTool("getMemories", OBJECT_MAPPER.createObjectNode().put("appId", appId));
        JsonNode page = OBJECT_MAPPER.readTree(result.at("/content/0/text").asText());
        assertTrue(page.withArray("memories").isEmpty(), () -> "cleanup left memories in " + appId);
        assertTrue(page.path("nextCursor").isNull(), "empty namespace must not have a next cursor");
    }

    private void deleteMemory(String id) throws Exception {
        callTool("deleteMemory", OBJECT_MAPPER.createObjectNode().put("id", id));
    }

    private JsonNode callTool(String name, ObjectNode arguments) throws IOException, InterruptedException {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode()
                .put("jsonrpc", "2.0")
                .put("id", ++requestId)
                .put("method", "tools/call");
        payload.putObject("params").put("name", name).set("arguments", arguments);

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());

        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        assertEquals("2.0", body.path("jsonrpc").asText());
        assertFalse(body.has("error"), response.body());
        assertTrue(body.has("result"), response.body());
        assertFalse(body.path("result").path("isError").asBoolean(true), response.body());
        return body.path("result");
    }

    private Set<String> fixtureIds(List<String> fixtureKeys) {
        Set<String> ids = new HashSet<>();
        for (String fixtureKey : fixtureKeys) {
            CreatedMemory memory = fixtureMemories.get(fixtureKey);
            assertNotNull(memory, () -> "dataset references unknown fixture " + fixtureKey);
            ids.add(memory.id());
        }
        return ids;
    }

    private Set<String> fixtureIdsForNamespace(String namespace) {
        return fixtureMemories.values().stream()
                .filter(memory -> namespace.equals(memory.namespace()))
                .map(CreatedMemory::id)
                .collect(java.util.stream.Collectors.toSet());
    }

    private int firstRelevantRank(List<JsonNode> results, Set<String> expectedIds) {
        for (int index = 0; index < results.size(); index++) {
            if (expectedIds.contains(results.get(index).path("id").asText())) {
                return index + 1;
            }
        }
        return 0;
    }

    private void printObservedRanking(EvaluationCase evaluationCase, List<JsonNode> results) {
        List<String> observedFixtures = results.stream()
                .map(memory -> fixtureKeyForId(memory.path("id").asText()))
                .toList();
        System.out.printf("Retrieval evaluation case=%s expected=%s observedFixtures=%s%n",
                evaluationCase.name(), evaluationCase.mustContainFixtureKeys(), observedFixtures);
    }

    private String fixtureKeyForId(String id) {
        return fixtureMemories.values().stream()
                .filter(memory -> id.equals(memory.id()))
                .map(CreatedMemory::key)
                .findFirst()
                .orElse("unknown:" + id);
    }

    private String appIdFor(String namespace) {
        return switch (namespace) {
            case "primary" -> primaryAppId;
            case "control" -> controlAppId;
            default -> throw new IllegalArgumentException("Unknown evaluation namespace: " + namespace);
        };
    }

    private String oppositeNamespace(String namespace) {
        return switch (namespace) {
            case "primary" -> "control";
            case "control" -> "primary";
            default -> throw new IllegalArgumentException("Unknown evaluation namespace: " + namespace);
        };
    }

    private Exception addCleanupFailure(Exception current, Exception next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private String requiredEnvironment() {
        String value = System.getenv("RECALL_API_KEY");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + "RECALL_API_KEY" + " before running ./gradlew liveIntegrationTest");
        }
        return value;
    }

    private record EvaluationDataset(List<Fixture> fixtures, List<EvaluationCase> cases) {
    }

    private record Fixture(String key, String namespace, String text) {
    }

    private record EvaluationCase(String name, String namespace, String query, int topN,
                                  List<String> mustContainFixtureKeys) {
    }

    private record CreatedMemory(String key, String namespace, String id) {
    }
}

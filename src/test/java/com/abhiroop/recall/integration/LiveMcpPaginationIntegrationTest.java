package com.abhiroop.recall.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in deployed integration coverage. Run with {@code ./gradlew liveIntegrationTest}; it is
 * intentionally excluded from the default cloud-free test task.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("live")
class LiveMcpPaginationIntegrationTest {

    private static final String DEFAULT_URL = "https://recall.abhiroop.dev/v1/mcp";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<String> createdIds = new ArrayList<>();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private String apiKey;
    private String appId;
    private String emptyAppId;
    private URI endpoint;
    private Firestore firestore;
    private int requestId;

    @BeforeAll
    void configureLiveDependencies() {
        apiKey = requiredEnvironment("RECALL_API_KEY");
        String projectId = requiredEnvironment("RECALL_GCP_PROJECT_ID");
        endpoint = URI.create(System.getenv().getOrDefault("RECALL_URL", DEFAULT_URL));
        appId = "recall-pagination-" + UUID.randomUUID();
        emptyAppId = appId + "-empty";
        firestore = FirestoreOptions.newBuilder().setProjectId(projectId).build().getService();

        System.out.printf("Live pagination test endpoint=%s project=%s appId=%s%n", endpoint, projectId, appId);
    }

    @AfterAll
    void cleanUpAndCloseFirestore() throws Exception {
        try {
            cleanUpFixtures();
        } finally {
            if (firestore != null) {
                firestore.close();
            }
        }
    }

    @Test
    void deployedMcpPaginatesSameTimestampFixturesAndCleansThemUp() throws Exception {
        assertEmptyPage(getPage(emptyAppId, null));

        Timestamp createdAt = Timestamp.ofTimeSecondsAndNanos(1_700_000_000L, 123_456_789);
        List<String> expectedIds = seedFixtures(createdAt);

        JsonNode firstPage = getPage(appId, null);
        JsonNode secondPage = getPage(appId, firstPage.path("nextCursor"));
        JsonNode thirdPage = getPage(appId, secondPage.path("nextCursor"));

        verifyTwentyOneRecordPagination(expectedIds, firstPage, secondPage, thirdPage);

        String lastId = memoryIds(thirdPage).getFirst();
        deleteMemory(lastId);
        createdIds.remove(lastId);

        JsonNode exactMultipleFirst = getPage(appId, null);
        JsonNode exactMultipleSecond = getPage(appId, exactMultipleFirst.path("nextCursor"));
        JsonNode exactMultipleTerminal = getPage(appId, exactMultipleSecond.path("nextCursor"));
        verifyExactMultiplePagination(expectedIds, exactMultipleFirst, exactMultipleSecond, exactMultipleTerminal);
    }

    private void verifyTwentyOneRecordPagination(
            List<String> expectedIds,
            JsonNode firstPage,
            JsonNode secondPage,
            JsonNode thirdPage
    ) {
        assertAll(
                () -> assertEquals(expectedIds.subList(0, 10), memoryIds(firstPage)),
                () -> assertNotNull(firstPage.get("nextCursor")),
                () -> assertFalse(firstPage.path("nextCursor").isNull()),
                () -> assertEquals(expectedIds.subList(10, 20), memoryIds(secondPage)),
                () -> assertFalse(secondPage.path("nextCursor").isNull()),
                () -> assertEquals(expectedIds.subList(20, 21), memoryIds(thirdPage)),
                () -> assertTrue(thirdPage.path("nextCursor").isNull())
        );
    }

    private void verifyExactMultiplePagination(
            List<String> expectedIds,
            JsonNode exactMultipleFirst,
            JsonNode exactMultipleSecond,
            JsonNode exactMultipleTerminal
    ) {
        assertAll(
                () -> assertEquals(expectedIds.subList(0, 10), memoryIds(exactMultipleFirst)),
                () -> assertFalse(exactMultipleFirst.path("nextCursor").isNull()),
                () -> assertEquals(expectedIds.subList(10, 20), memoryIds(exactMultipleSecond)),
                () -> assertFalse(exactMultipleSecond.path("nextCursor").isNull()),
                () -> assertTrue(memoryIds(exactMultipleTerminal).isEmpty()),
                () -> assertTrue(exactMultipleTerminal.path("nextCursor").isNull())
        );
    }

    private List<String> seedFixtures(Timestamp createdAt) throws Exception {
        List<String> ids = new ArrayList<>();
        String runId = UUID.randomUUID().toString();
        for (int index = 0; index < 21; index++) {
            String id = "pagination-%s-%02d".formatted(runId, index);
            firestore.collection("memories").document(id).set(Map.of(
                    "appId", appId,
                    "text", "Pagination fixture " + id,
                    "createdAt", createdAt
            )).get();
            createdIds.add(id);
            ids.add(id);
        }
        return ids.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private JsonNode getPage(String requestedAppId, JsonNode cursor) throws Exception {
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode().put("appId", requestedAppId);
        if (cursor != null && !cursor.isNull()) {
            arguments.set("cursor", cursor);
        }
        JsonNode result = callTool("getMemories", arguments);
        JsonNode page = OBJECT_MAPPER.readTree(result.at("/content/0/text").asText());
        assertTrue(page.isObject() && page.has("memories") && page.has("nextCursor"), "getMemories must return a page object");
        assertTrue(memoryIds(page).stream().allMatch(id -> id.startsWith("pagination-") || requestedAppId.equals(emptyAppId)));
        return page;
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
        assertAll(
                () -> assertEquals("2.0", body.path("jsonrpc").asText()),
                () -> assertFalse(body.has("error"), response.body()),
                () -> assertTrue(body.has("result"), response.body()),
                () -> assertFalse(body.path("result").path("isError").asBoolean(true), response.body())
        );
        return body.path("result");
    }

    private List<String> memoryIds(JsonNode page) {
        List<String> ids = new ArrayList<>();
        for (JsonNode memory : page.withArray("memories")) {
            assertEquals(appId, memory.path("appId").asText(), "retrieval crossed the requested app ID");
            ids.add(memory.path("id").asText());
        }
        return ids;
    }

    private void assertEmptyPage(JsonNode page) {
        assertTrue(memoryIds(page).isEmpty());
        assertTrue(page.path("nextCursor").isNull());
    }

    private void cleanUpFixtures() throws Exception {
        Exception cleanupFailure = null;
        for (String id : List.copyOf(createdIds)) {
            try {
                deleteMemory(id);
                createdIds.remove(id);
            } catch (Exception exception) {
                if (cleanupFailure == null) {
                    cleanupFailure = exception;
                } else {
                    cleanupFailure.addSuppressed(exception);
                }
            }
        }

        try {
            assertEmptyPage(getPage(appId, null));
            assertEmptyPage(getPage(emptyAppId, null));
        } catch (Exception exception) {
            if (cleanupFailure == null) {
                cleanupFailure = exception;
            } else {
                cleanupFailure.addSuppressed(exception);
            }
        }

        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + name + " before running ./gradlew liveIntegrationTest");
        }
        return value;
    }
}

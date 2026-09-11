package com.abhiroop.recall.filter;

import com.abhiroop.recall.entity.Memory;
import com.abhiroop.recall.repository.MemoryRepository;
import com.abhiroop.recall.support.CloudFreeMcpTestConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.IntStream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = CloudFreeMcpTestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.config.import=",
                "sm@RECALL_API_KEY=test-api-key",
                "spring.cloud.gcp.core.enabled=false",
                "spring.cloud.gcp.secretmanager.enabled=false",
                "spring.cloud.gcp.firestore.enabled=false"
        }
)
@AutoConfigureMockMvc
class RecallMcpToolFlowMvcTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
            """;

    private static final String TOOLS_LIST_REQUEST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
            """;

    private static final String GET_MEMORIES_REQUEST = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"getMemories","arguments":{"appId":"test-app"}}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemoryRepository memoryRepository;

    @Test
    void authenticatedClientCanInitializeDiscoverAndCallRealMcpTools() throws Exception {
        when(memoryRepository.findByAppId("test-app", 10, null, null))
                .thenReturn(List.of(Memory.builder()
                        .id("memory-1")
                        .appId("test-app")
                        .text("deterministic memory")
                        .createdAt(Timestamp.ofTimeSecondsAndNanos(1_700_000_000L, 0))
                        .build()));

        performMcpRequest(INITIALIZE_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.serverInfo.name").value("recall-mcp"))
                .andExpect(jsonPath("$.error").doesNotExist());

        performMcpRequest(TOOLS_LIST_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.result.tools[?(@.name == 'getMemories')]").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());

        performMcpRequest(GET_MEMORIES_REQUEST)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("memory-1"),
                        org.hamcrest.Matchers.containsString("deterministic memory")
                )))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(memoryRepository).findByAppId("test-app", 10, null, null);
    }

    @Test
    void authenticatedClientCanRelayPaginationCursorsThroughTheRealMcpTransport() throws Exception {
        Timestamp createdAt = Timestamp.ofTimeSecondsAndNanos(1_700_000_000L, 123_456_789);
        List<Memory> memories = IntStream.range(0, 21)
                .mapToObj(index -> Memory.builder()
                        .id("memory-%02d".formatted(index))
                        .appId("pagination-app")
                        .text("pagination fixture %d".formatted(index))
                        .createdAt(createdAt)
                        .build())
                .toList();

        when(memoryRepository.findByAppId("pagination-app", 10, null, null))
                .thenReturn(memories.subList(0, 10));
        when(memoryRepository.findByAppId("pagination-app", 10, createdAt, "memory-09"))
                .thenReturn(memories.subList(10, 20));
        when(memoryRepository.findByAppId("pagination-app", 10, createdAt, "memory-19"))
                .thenReturn(memories.subList(20, 21));

        JsonNode firstPage = getMemoriesPage(10, null);
        JsonNode secondPage = getMemoriesPage(11, firstPage.path("nextCursor"));
        JsonNode thirdPage = getMemoriesPage(12, secondPage.path("nextCursor"));

        org.junit.jupiter.api.Assertions.assertAll(
                () -> org.junit.jupiter.api.Assertions.assertEquals(
                        IntStream.range(0, 10).mapToObj("memory-%02d"::formatted).toList(),
                        memoryIds(firstPage)
                ),
                () -> org.junit.jupiter.api.Assertions.assertTrue(firstPage.path("nextCursor").isObject()),
                () -> org.junit.jupiter.api.Assertions.assertEquals(
                        IntStream.range(10, 20).mapToObj("memory-%02d"::formatted).toList(),
                        memoryIds(secondPage)
                ),
                () -> org.junit.jupiter.api.Assertions.assertTrue(secondPage.path("nextCursor").isObject()),
                () -> org.junit.jupiter.api.Assertions.assertEquals(List.of("memory-20"), memoryIds(thirdPage)),
                () -> org.junit.jupiter.api.Assertions.assertTrue(thirdPage.path("nextCursor").isNull())
        );

        verify(memoryRepository).findByAppId("pagination-app", 10, null, null);
        verify(memoryRepository).findByAppId("pagination-app", 10, createdAt, "memory-09");
        verify(memoryRepository).findByAppId("pagination-app", 10, createdAt, "memory-19");
    }

    private org.springframework.test.web.servlet.ResultActions performMcpRequest(String request) throws Exception {
        return mockMvc.perform(post("/v1/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(request));
    }

    private JsonNode getMemoriesPage(int requestId, JsonNode cursor) throws Exception {
        String request = cursor == null ?
                """
                        {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"getMemories","arguments":{"appId":"pagination-app"}}}
                        """.formatted(requestId) :
                """
                        {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"getMemories","arguments":{"appId":"pagination-app","cursor":%s}}}
                        """.formatted(requestId, cursor);

        String response = performMcpRequest(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(requestId))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return OBJECT_MAPPER.readTree(OBJECT_MAPPER.readTree(response).at("/result/content/0/text").asText());
    }

    private List<String> memoryIds(JsonNode page) {
        return IntStream.range(0, page.withArray("memories").size())
                .mapToObj(index -> page.withArray("memories").get(index).path("id").asText())
                .toList();
    }
}

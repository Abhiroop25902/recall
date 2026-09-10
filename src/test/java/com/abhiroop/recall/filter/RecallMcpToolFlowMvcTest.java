package com.abhiroop.recall.filter;

import com.abhiroop.recall.entity.Memory;
import com.abhiroop.recall.repository.MemoryRepository;
import com.abhiroop.recall.support.CloudFreeMcpTestConfiguration;
import com.google.cloud.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        when(memoryRepository.findByAppId(eq("test-app"), eq(10), isNull(), isNull()))
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

    private org.springframework.test.web.servlet.ResultActions performMcpRequest(String request) throws Exception {
        return mockMvc.perform(post("/v1/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(request));
    }
}

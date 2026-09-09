package com.abhiroop.recall.filter;

import com.abhiroop.recall.support.CloudFreeMcpTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                "spring.cloud.gcp.firestore.enabled=false",
                "spring.ai.mcp.server.streamable-http.mcp-endpoint=/test/mcp",
                "server.servlet.context-path=/recall"
        }
)
@AutoConfigureMockMvc
class RecallMcpConfiguredRouteMvcTest {

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextPathHealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/recall/v1/health").contextPath("/recall"))
                .andExpect(status().isOk());
    }

    @Test
    void configuredMcpEndpointRejectsMissingCredentials() throws Exception {
        mockMvc.perform(post("/recall/test/mcp").contextPath("/recall"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void configuredMcpEndpointWithContextPathReachesRealMcpInitialize() throws Exception {
        mockMvc.perform(post("/recall/test/mcp")
                        .contextPath("/recall")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(INITIALIZE_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.serverInfo.name").value("recall-mcp"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void staleDefaultMcpRouteIsNotAvailable() throws Exception {
        mockMvc.perform(post("/recall/v1/mcp").contextPath("/recall"))
                .andExpect(status().isNotFound());
    }
}

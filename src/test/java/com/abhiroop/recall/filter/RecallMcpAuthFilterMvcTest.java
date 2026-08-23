package com.abhiroop.recall.filter;

import com.abhiroop.recall.config.RecallMcpSecurityConfig;
import com.abhiroop.recall.controller.HealthController;
import com.abhiroop.recall.service.MemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = HealthController.class,
        properties = {
                "spring.config.import=",
                "sm@RECALL_API_KEY=test-api-key"
        }
)
@Import({RecallMcpSecurityConfig.class, RecallMcpAuthFilterMvcTest.McpProbeController.class})
class RecallMcpAuthFilterMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemoryService memoryService;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("recall"));
    }

    @Test
    void mcpEndpointRejectsMissingCredentials() throws Exception {
        mockMvc.perform(post("/v1/mcp"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Missing or malformed Authorization header."));
    }

    @Test
    void mcpEndpointRejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/v1/mcp").header("Authorization", "Bearer wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid MCP token credentials."));
    }

    @Test
    void mcpEndpointAcceptsValidCredentials() throws Exception {
        mockMvc.perform(post("/v1/mcp").header("Authorization", "Bearer test-api-key"))
                .andExpect(status().isNoContent());
    }

    @RestController
    static class McpProbeController {

        @PostMapping("/v1/mcp")
        ResponseEntity<Void> handleMcpRequest() {
            return ResponseEntity.noContent().build();
        }
    }
}

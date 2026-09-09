package com.abhiroop.recall.filter;

import com.abhiroop.recall.RecallApplication;
import com.abhiroop.recall.config.RecallMcpSecurityConfig;
import com.abhiroop.recall.controller.HealthController;
import com.abhiroop.recall.repository.MemoryRepository;
import com.abhiroop.recall.service.MemoryService;
import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = RecallMcpAuthFilterMvcTest.CloudFreeMcpTestConfiguration.class,
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
class RecallMcpAuthFilterMvcTest {

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("recall"));
    }

    @ParameterizedTest
    @MethodSource("httpMethods")
    void activeMcpEndpointRejectsMissingCredentialsForEveryHttpMethod(HttpMethod method) throws Exception {
        mockMvc.perform(request(method, "/v1/mcp"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    static Stream<HttpMethod> httpMethods() {
        return Stream.of(
                HttpMethod.GET,
                HttpMethod.HEAD,
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.PATCH,
                HttpMethod.DELETE,
                HttpMethod.OPTIONS
        );
    }

    @Test
    void traceMcpEndpointIsRejectedBeforeRouting() throws Exception {
        mockMvc.perform(request(HttpMethod.TRACE, "/v1/mcp"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingCredentialsReturnGenericBearerChallenge() throws Exception {
        mockMvc.perform(post("/v1/mcp"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(HttpStatus.UNAUTHORIZED.toString()));
    }

    @ParameterizedTest
    @MethodSource("malformedOrInvalidAuthorizationHeaders")
    void malformedOrInvalidCredentialsReturnGenericBearerChallenge(String authorizationHeader) throws Exception {
        mockMvc.perform(post("/v1/mcp").header(HttpHeaders.AUTHORIZATION, authorizationHeader))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(HttpStatus.UNAUTHORIZED.toString()));
    }

    static Stream<String> malformedOrInvalidAuthorizationHeaders() {
        return Stream.of("Basic test-api-key", "Bearer wrong-api-key");
    }

    @Test
    void cloudFreeFixtureHasNoGoogleCloudClients() {
        assertThat(applicationContext.getBeansOfType(CredentialsProvider.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(Firestore.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(GoogleGenAiTextEmbeddingModel.class)).isEmpty();
    }

    @Test
    void validCredentialReachesRealMcpInitialize() throws Exception {
        mockMvc.perform(post("/v1/mcp")
                        .header("Authorization", "Bearer test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(INITIALIZE_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.serverInfo.name").value("recall-mcp"))
                .andExpect(jsonPath("$.result.instructions").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("save the replacement and confirm its returned ID before separately deleting the obsolete memory"),
                        org.hamcrest.Matchers.containsString("never blindly retry"),
                        org.hamcrest.Matchers.containsString("stop for audit if reconciliation is inconclusive")
                )))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
            GoogleGenAiTextEmbeddingAutoConfiguration.class
    })
    @Import({RecallMcpSecurityConfig.class, HealthController.class})
    static class CloudFreeMcpTestConfiguration {

        @Bean
        MemoryRepository memoryRepository() {
            return mock(MemoryRepository.class);
        }

        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }

        @Bean
        MemoryService memoryService(MemoryRepository memoryRepository, EmbeddingModel embeddingModel) {
            return new MemoryService(memoryRepository, embeddingModel);
        }

        @Bean
        ToolCallbackProvider memoryTools(MemoryService memoryService) {
            return new RecallApplication().memoryTools(memoryService);
        }
    }
}

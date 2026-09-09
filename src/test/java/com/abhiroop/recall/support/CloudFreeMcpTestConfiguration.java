package com.abhiroop.recall.support;

import com.abhiroop.recall.RecallApplication;
import com.abhiroop.recall.config.RecallMcpSecurityConfig;
import com.abhiroop.recall.controller.HealthController;
import com.abhiroop.recall.repository.MemoryRepository;
import com.abhiroop.recall.service.MemoryService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration;
import org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiTextEmbeddingAutoConfiguration;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.mock;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {
        GoogleGenAiEmbeddingConnectionAutoConfiguration.class,
        GoogleGenAiTextEmbeddingAutoConfiguration.class
})
@Import({RecallMcpSecurityConfig.class, HealthController.class})
public class CloudFreeMcpTestConfiguration {

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

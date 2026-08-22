package com.abhiroop.recall;

import com.abhiroop.recall.service.MemoryService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RecallApplication {

    static void main(String[] args) {
        SpringApplication.run(RecallApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider memoryTools(MemoryService memoryService) {
        return MethodToolCallbackProvider
                .builder()
                .toolObjects(memoryService)
                .build();
    }

}

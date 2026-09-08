package com.abhiroop.recall.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

class RecallMcpSecurityConfigTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n"})
    void rejectsMissingOrBlankApiKey(String recallApiKey) {
        assertThatIllegalStateException().isThrownBy(() -> new RecallMcpSecurityConfig(
                recallApiKey,
                mock(McpServerStreamableHttpProperties.class)
        ));
    }
}

package com.abhiroop.recall.config;

import com.abhiroop.recall.filter.RecallMcpAuthFilter;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(McpServerStreamableHttpProperties.class)
public class RecallMcpSecurityConfig {

    private final RecallMcpAuthFilter authFilter;
    private final RequestMatcher mcpEndpointMatcher;

    @Autowired
    public RecallMcpSecurityConfig(
            @Value("${sm@RECALL_API_KEY}") String recallApiKey,
            McpServerStreamableHttpProperties properties
    ) {
        if (recallApiKey == null || recallApiKey.isBlank()) {
            throw new IllegalStateException("RECALL_API_KEY must be configured in Google Cloud Secrets");
        }

        this.mcpEndpointMatcher = PathPatternRequestMatcher.pathPattern(properties.getMcpEndpoint());
        this.authFilter = new RecallMcpAuthFilter(recallApiKey, mcpEndpointMatcher);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(mcpEndpointMatcher).authenticated()
                        .requestMatchers("/v1/health").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

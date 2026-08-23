package com.abhiroop.recall.config;

import com.abhiroop.recall.filter.RecallMcpAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class RecallMcpSecurityConfig {

    private final RecallMcpAuthFilter authFilter;

    @Autowired
    public RecallMcpSecurityConfig(@Value("${sm@RECALL_API_KEY}") String recallApiKey) {
        this.authFilter = new RecallMcpAuthFilter(recallApiKey);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/mcp").authenticated()
                        .requestMatchers("/v1/health").permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

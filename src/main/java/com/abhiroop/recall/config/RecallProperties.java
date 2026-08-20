package com.abhiroop.recall.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// this maps to application.properties
@ConfigurationProperties(prefix = "recall")
public record RecallProperties(
        String gcpProjectId //recall.gcp-project-id
) {
}

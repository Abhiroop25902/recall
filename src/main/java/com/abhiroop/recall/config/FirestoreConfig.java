package com.abhiroop.recall.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@EnableConfigurationProperties(RecallProperties.class)
public class FirestoreConfig {

    @Bean
    public Firestore getFireStore(RecallProperties properties) throws IOException {
        return FirestoreOptions.getDefaultInstance()
                .toBuilder()
                .setProjectId(properties.gcpProjectId())
                .build()
                .getService();
    }
}

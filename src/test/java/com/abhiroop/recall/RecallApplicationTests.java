package com.abhiroop.recall;

import com.abhiroop.recall.support.CloudFreeMcpTestConfiguration;
import com.google.api.gax.core.CredentialsProvider;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CloudFreeMcpTestConfiguration.class, properties = {
        "spring.config.import=",
        "sm@RECALL_API_KEY=test-api-key",
        "spring.cloud.gcp.core.enabled=false",
        "spring.cloud.gcp.secretmanager.enabled=false",
        "spring.cloud.gcp.firestore.enabled=false"
})
class RecallApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
    }

    @Test
    void contextHasNoGoogleCloudClients() {
        assertThat(applicationContext.getBeansOfType(CredentialsProvider.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(Firestore.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(GoogleGenAiTextEmbeddingModel.class)).isEmpty();
    }

}

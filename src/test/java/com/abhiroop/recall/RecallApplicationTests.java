package com.abhiroop.recall;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.config.import=",
        "sm@RECALL_API_KEY=test-api-key"
})
class RecallApplicationTests {

    @Test
    void contextLoads() {
    }

}

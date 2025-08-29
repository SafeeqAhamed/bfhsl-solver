package com.example.bfhsl;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@SpringBootApplication
public class BfhslSolverApplication {

    // Candidate details
    private static final String NAME = "Safeeq Ahamed";
    private static final String REG_NO = "22BCE5111";  
    private static final String EMAIL = "ssafeeq2004@gmail,com";

    private static final String GENERATE_URL =
            "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

    private static final String FALLBACK_SUBMIT_URL =
            "https://bfhldevapigw.healthrx.co.in/hiring/testWebhook/JAVA";

    // Final SQL (from Question 1)
    private static final String FINAL_SQL = """
        SELECT p.AMOUNT AS SALARY,
               CONCAT(e.FIRST_NAME, ' ', e.LAST_NAME) AS NAME,
               TIMESTAMPDIFF(YEAR, e.DOB, CURDATE()) AS AGE,
               d.DEPARTMENT_NAME
        FROM PAYMENTS p
        JOIN EMPLOYEE e ON p.EMP_ID = e.EMP_ID
        JOIN DEPARTMENT d ON e.DEPARTMENT = d.DEPARTMENT_ID
        WHERE DAY(p.PAYMENT_TIME) <> 1
          AND p.AMOUNT = (
              SELECT MAX(AMOUNT)
              FROM PAYMENTS
              WHERE DAY(PAYMENT_TIME) <> 1
          );
        """;

    public static void main(String[] args) {
        SpringApplication.run(BfhslSolverApplication.class, args);
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ApplicationRunner runner(WebClient webClient) {
        return args -> {
            System.out.println("▶ Starting workflow...");

            // Step 1: Generate webhook
            Map<String, String> reqBody = Map.of(
                    "name", NAME,
                    "regNo", REG_NO,
                    "email", EMAIL
            );

            GenerateResponse genResp = webClient.post()
                    .uri(GENERATE_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(reqBody))
                    .retrieve()
                    .bodyToMono(GenerateResponse.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            if (genResp == null) {
                System.err.println("✘ generateWebhook returned null");
                return;
            }

            System.out.println("✔ Webhook received: " + genResp.getWebhook());

            // Step 2: Submit solution
            String targetUrl = (genResp.getWebhook() != null && !genResp.getWebhook().isBlank())
                    ? genResp.getWebhook()
                    : FALLBACK_SUBMIT_URL;

            String authHeader = "Bearer " + genResp.getAccessToken();

            Map<String, String> submitBody = Map.of("finalQuery", FINAL_SQL);

            String resp = webClient.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .body(BodyInserters.fromValue(submitBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

            System.out.println("✔ Submission done. Response: " + resp);
        };
    }

    public static class GenerateResponse {
        private String webhook;
        private String accessToken;

        public String getWebhook() { return webhook; }
        public void setWebhook(String webhook) { this.webhook = webhook; }
        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    }
}

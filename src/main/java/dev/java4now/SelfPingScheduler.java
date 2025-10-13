package dev.java4now;

import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.Date;

@Component
public class SelfPingScheduler {

    private final RestTemplate restTemplate;

    public SelfPingScheduler(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedRate = 270000) // 4.5 minuta
    public void selfPing() {
        try {
            String port = System.getenv("PORT");
            if (port == null) {
                port = "8080";
            }

            String response = restTemplate.getForObject(
                    "http://localhost:" + port + "/health",
                    String.class
            );
            System.out.println("✓ Keep-alive ping successful: " + new Date());

        } catch (Exception e) {
            System.out.println("✗ Keep-alive ping failed: " + e.getMessage());

            // Probaj i root endpoint kao fallback
            try {
                String port = System.getenv("PORT");
                if (port == null) port = "8080";
                restTemplate.getForObject("http://localhost:" + port + "/", String.class);
                System.out.println("✓ Fallback ping successful");
            } catch (Exception ex) {
                System.out.println("✗ Fallback ping also failed");
            }
        }
    }
}

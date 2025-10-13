package dev.java4now;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.Date;

@Component
public class SelfPingScheduler {

    private final RestTemplate restTemplate;

    // Constructor injection - Spring će automatski inject-ovati RestTemplate
    public SelfPingScheduler(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedRate = 270000) // 4.5 minuta
    public void selfPing() {
        try {
            String port = System.getenv("PORT");
            if (port == null) {
                port = "8080"; // default port za development
            }

            String url = "http://localhost:" + port + "/health";
            System.out.println("Pinging: " + url);

            String response = restTemplate.getForObject(url, String.class);
            System.out.println("Self-ping successful: " + response);

        } catch (Exception e) {
            System.out.println("Self-ping failed: " + e.getMessage());
        }
    }
}

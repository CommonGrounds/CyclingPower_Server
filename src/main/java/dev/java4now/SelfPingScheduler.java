package dev.java4now;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestOperations;

import java.util.Date;

@Component
public class SelfPingScheduler {

    private RestOperations restTemplate;

    @PostConstruct
    public void init() {
        String port = System.getenv("PORT");
        System.out.println("Using PORT: " + port);
    }

    @Scheduled(fixedRate = 270000)
    public void selfPing() {
        try {
            String port = System.getenv("PORT");
            String url = "http://localhost:" + port + "/health";
            System.out.println("Pinging: " + url);

            restTemplate.getForObject(url, String.class);
            System.out.println("Self-ping successful at: " + new Date());
        } catch (Exception e) {
            System.out.println("Self-ping failed: " + e.getMessage());
        }
    }
}

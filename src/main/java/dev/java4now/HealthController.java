package dev.java4now;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK - " + new Date());
    }

    // Dodaj i root endpoint za dodatnu sigurnost
    @GetMapping("/")
    public ResponseEntity<String> home() {
        return ResponseEntity.ok("Server is running - " + new Date());
    }
}

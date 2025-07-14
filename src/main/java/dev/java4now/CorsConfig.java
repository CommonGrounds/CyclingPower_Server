package dev.java4now;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    /*
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Allow all endpoints
                .allowedOrigins("http://localhost:8080", "http://localhost:63342", "http://127.0.0.1:9876") // Allow these origins
                .allowedMethods("GET", "POST", "PUT", "DELETE") // Allow these HTTP methods
                .allowedHeaders("*") // Allow all headers
                .allowCredentials(true); // Allow credentials
    }
// IMPORTANT - Allow WebFX front-end - kada je file server ( development ) http://localhost:63342  ( Chrome )
    */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Allow all endpoints
                .allowedOriginPatterns(
                        "http://localhost:*",       // Local development
                        "http://127.0.0.1:*",      // Local IP
                        "https://staging.example.com", // Staging
                        "https://*.example.com"     // All production subdomains
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*") // Allow all headers
                .allowCredentials(false); // Allow credentials
    /*
    If you later decide to use credentials (e.g., for authentication), you will need to:
       Set allowCredentials(true).
       Explicitly specify allowed origins (e.g., "http://localhost:8080") instead of using *.
     */
    }
/*
This configuration allows requests from multiple domains and ports while maintaining security.
     */
}
package dev.java4now;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;

@RestController
public class DebugController {
    @Autowired
    private DataSource dataSource;

    @GetMapping("/api/debug-db")
    public String debugDatabase() {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String dbPath = dataSource.getConnection().getMetaData().getURL();
            StringBuilder result = new StringBuilder("Database path: " + dbPath + "\n");

            // Check if 'users' table exists
            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='APP_USER'",
                    Integer.class
            ) > 0;
            result.append("Users table exists: ").append(tableExists).append("\n");

            if (tableExists) {
                // Count users
                Integer userCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM APP_USER",
                        Integer.class
                );
                result.append("User count: ").append(userCount).append("\n");

                // Optional: List usernames (adjust column name if different)
                String users = jdbcTemplate.queryForList(
                        "SELECT NAME FROM APP_USER",
                        String.class
                ).toString();
                result.append("Usernames: ").append(users).append("\n");
            } else {
                result.append("No 'users' table found in the database.\n");
            }

            return result.toString();
        } catch (SQLException e) {
            return "Error connecting to database: " + e.getMessage();
        } catch (Exception e) {
            return "Error querying database: " + e.getMessage();
        }
    }


    @GetMapping("/api/download-db")
    public ResponseEntity<Resource> downloadDb() throws IOException {
        File file = new File("./cycling_power.db");
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=cycling_power.db")
                .contentLength(file.length())
                .body(resource);
    }


    @PostMapping("/api/upload-db")
    public ResponseEntity<String> uploadDb(@RequestParam("file") MultipartFile file) throws IOException {
        file.transferTo(new File("./app/cycling_power.db"));
        return ResponseEntity.ok("Database uploaded");
    }
}

package dev.java4now;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
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
}

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
            // Check connection
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String dbPath = dataSource.getConnection().getMetaData().getURL();
            Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM APP_USER", Integer.class); // Adjust table name
            return "Database path: " + dbPath + "\nUser count: " + userCount;
        } catch (SQLException e) {
            return "Error connecting to database: " + e.getMessage();
        }
    }
}

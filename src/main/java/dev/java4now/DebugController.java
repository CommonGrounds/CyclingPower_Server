package dev.java4now;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@RestController
public class DebugController {
    @Autowired
    private DataSource dataSource;
    private static final String JSON_DIR = "/app/json"; // Absolute path for Render
    private static final String IMAGE_DIR = "/app/images"; // Absolute path for Render
    private static final String UPLOAD_DIR = "Uploads/";
    private static final String DB_PATH = "cycling_power.db";

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
        file.transferTo(new File("./cycling_power.db"));
        return ResponseEntity.ok("Database uploaded");
    }



    @GetMapping("/api/backup-all-json-public")
    public ResponseEntity<Resource> backupAllJsonFilesPublic() throws IOException {
        Path jsonDir = Paths.get(JSON_DIR);
        System.out.println("Checking directory: " + jsonDir.toAbsolutePath());
        if (!Files.exists(jsonDir)) {
            System.out.println("Directory does not exist: " + jsonDir);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        if (!Files.isDirectory(jsonDir)) {
            System.out.println("Path is not a directory: " + jsonDir);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        List<Path> jsonFiles = Files.list(jsonDir)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .collect(Collectors.toList());
        System.out.println("Found " + jsonFiles.size() + " JSON files: " + jsonFiles);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Path filePath : jsonFiles) {
                ZipEntry entry = new ZipEntry(filePath.getFileName().toString());
                zos.putNextEntry(entry);
                Files.copy(filePath, zos);
                zos.closeEntry();
            }
            zos.finish();
        }

        byte[] zipBytes = baos.toByteArray();
        if (zipBytes.length == 0) {
            System.out.println("No JSON files found, returning 404");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        Resource resource = new ByteArrayResource(zipBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=all_json_backup_public.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }


    @GetMapping("/api/backup-all-images-public")
    public ResponseEntity<Resource> backupAllImageFilesPublic() throws IOException {
        Path imageDir = Paths.get(IMAGE_DIR);
        System.out.println("Checking image directory: " + imageDir.toAbsolutePath());
        if (!Files.exists(imageDir)) {
            System.out.println("Image directory does not exist: " + imageDir);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        if (!Files.isDirectory(imageDir)) {
            System.out.println("Image path is not a directory: " + imageDir);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        List<Path> imageFiles = Files.list(imageDir)
                .filter(path -> path.getFileName().toString().matches(".*\\.(jpg|png|jpeg)"))
                .collect(Collectors.toList());
        System.out.println("Found " + imageFiles.size() + " image files: " + imageFiles);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Path filePath : imageFiles) {
                System.out.println("Adding image: " + filePath);
                ZipEntry entry = new ZipEntry(filePath.getFileName().toString());
                zos.putNextEntry(entry);
                Files.copy(filePath, zos);
                zos.closeEntry();
            }
            zos.finish();
        }

        byte[] zipBytes = baos.toByteArray();
        if (zipBytes.length == 0) {
            System.out.println("No image files found, returning 404");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        Resource resource = new ByteArrayResource(zipBytes);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=all_images_backup_public.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);

        // Optional token-based security (uncomment to enable)
        /*
        String expectedKey = "your-secret-key"; // Define a secret key
        if (!expectedKey.equals(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
        */
    }


    @GetMapping("/api/list-images-public")
    public ResponseEntity<List<String>> listImagesPublic() throws IOException {
        Path imageDir = Paths.get(IMAGE_DIR);
        if (!Files.exists(imageDir) || !Files.isDirectory(imageDir)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(List.of());
        }
        List<String> imageFiles = Files.list(imageDir)
                .filter(path -> path.getFileName().toString().matches(".*\\.(jpg|png|jpeg)"))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toList());
        return ResponseEntity.ok(imageFiles);
    }
}

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
import org.springframework.http.MediaType;

import javax.sql.DataSource;
import java.io.*;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
public class DebugController {
    @Autowired
    private DataSource dataSource;
    private static final String JSON_DIR = "/app/json";
    private static final String IMAGE_DIR = "/app/images";
    private static final String UPLOAD_DIR = "Uploads/";
    private static final String DB_PATH = "cycling_power.db";
    private static final String BACKUP_TOKEN = System.getenv("BACKUP_TOKEN") != null ? System.getenv("BACKUP_TOKEN") : "your-secret-key";

    @GetMapping("/api/debug-db")
    public String debugDatabase() {
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            String dbPath = dataSource.getConnection().getMetaData().getURL();
            StringBuilder result = new StringBuilder("Database path: " + dbPath + "\n");
            boolean tableExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='APP_USER'",
                    Integer.class
            ) > 0;
            result.append("Users table exists: ").append(tableExists).append("\n");
            if (tableExists) {
                Integer userCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM APP_USER",
                        Integer.class
                );
                result.append("User count: ").append(userCount).append("\n");
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
        commitToGit("Update database");
        return ResponseEntity.ok("Database uploaded");
    }


    private static final Object GIT_LOCK = new Object();

    private void commitToGit(String message, String... filesToAdd) {
        synchronized (GIT_LOCK) {
            try {
                ProcessBuilder pb = new ProcessBuilder();
                pb.directory(new File("/app"));
                pb.redirectErrorStream(true);

                // Check if .git exists
                if (!Files.exists(Paths.get("/app/.git"))) {
                    System.err.println("Git repository not found in /app");
                    return;
                }

                // Log current directory state
                pb.command("ls", "-la", "json/", "images/", "cycling_power.db");
                Process p = pb.start();
                System.out.println("Directory state: " + readProcessOutput(p));

                // Verify specific files exist and normalize paths
                List<String> normalizedFilesToAdd = new ArrayList<>();
                for (String filePath : filesToAdd) {
                    Path path = Paths.get(filePath).normalize();
                    if (Files.exists(path)) {
                        // Convert to path relative to /app
                        String relativePath = path.startsWith("/app/") ?
                                path.toString().substring(5) : path.toString();
                        normalizedFilesToAdd.add(relativePath);
                        System.out.println("File exists: " + relativePath);
                    } else {
                        System.err.println("File does not exist: " + path);
                    }
                }

                // Stash any existing changes
                pb.command("git", "stash", "push", "--include-untracked");
                p = pb.start();
                String stashOutput = readProcessOutput(p);
                int stashExit = p.waitFor();
                System.out.println("Git stash output: " + stashOutput);
                if (stashExit != 0) {
                    System.err.println("Git stash failed with exit code " + stashExit);
                }

                // Git pull --rebase
                String gitToken = System.getenv("GIT_TOKEN");
                if (gitToken == null || gitToken.isEmpty()) {
                    System.err.println("GIT_TOKEN not set");
                    return;
                }
                pb.command("git", "pull", "--rebase", "https://x:" + gitToken + "@github.com/CommonGrounds/CyclingPower_Server.git", "main");
                p = pb.start();
                String pullOutput = readProcessOutput(p);
                int pullExit = p.waitFor();
                System.out.println("Git pull --rebase output: " + pullOutput);
                if (pullExit != 0) {
                    System.err.println("Git pull --rebase failed with exit code " + pullExit);
                    pb.command("git", "stash", "pop");
                    p = pb.start();
                    System.out.println("Git stash pop output: " + readProcessOutput(p));
                    return;
                }

                // Add specific files and directories
                List<String> addCommand = new ArrayList<>(Arrays.asList("git", "add", "--force", "cycling_power.db", "json/", "images/"));
                addCommand.addAll(normalizedFilesToAdd);
                System.out.println("Executing git add command: " + String.join(" ", addCommand));
                pb.command(addCommand);
                p = pb.start();
                String addOutput = readProcessOutput(p);
                int addExit = p.waitFor();
                System.out.println("Git add output: " + addOutput);
                if (addExit != 0) {
                    System.err.println("Git add failed with exit code " + addExit);
                    pb.command("git", "stash", "pop");
                    p = pb.start();
                    System.out.println("Git stash pop output: " + readProcessOutput(p));
                    return;
                }

                // Verify staged changes
                pb.command("git", "status", "--porcelain");
                p = pb.start();
                String statusOutput = readProcessOutput(p);
                int statusExit = p.waitFor();
                System.out.println("Git status output: " + statusOutput);
                if (statusOutput.trim().isEmpty()) {
                    System.err.println("No changes staged for commit, checking untracked files");
                    pb.command("git", "ls-files", "--others", "--exclude-standard", "json/", "images/", "cycling_power.db");
                    p = pb.start();
                    String untrackedOutput = readProcessOutput(p);
                    System.out.println("Untracked files: " + untrackedOutput);
                    pb.command("git", "stash", "pop");
                    p = pb.start();
                    System.out.println("Git stash pop output: " + readProcessOutput(p));
                    return;
                }

                // Git commit
                pb.command("git", "commit", "-m", message);
                p = pb.start();
                String commitOutput = readProcessOutput(p);
                int commitExit = p.waitFor();
                System.out.println("Git commit output: " + commitOutput);
                if (commitExit != 0) {
                    System.err.println("Git commit failed with exit code " + commitExit);
                    pb.command("git", "stash", "pop");
                    p = pb.start();
                    System.out.println("Git stash pop output: " + readProcessOutput(p));
                    return;
                }

                // Git push
                pb.command("git", "push", "https://x:" + gitToken + "@github.com/CommonGrounds/CyclingPower_Server.git", "main");
                p = pb.start();
                String pushOutput = readProcessOutput(p);
                int pushExit = p.waitFor();
                System.out.println("Git push output: " + pushOutput);
                if (pushExit == 0) {
                    System.out.println("Successfully committed to Git: " + message);
                } else {
                    System.err.println("Git push failed with exit code " + pushExit);
                    pb.command("git", "stash", "pop");
                    p = pb.start();
                    System.out.println("Git stash pop output: " + readProcessOutput(p));
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Git operation failed: " + e.getMessage());
            }
        }
    }


    private String readProcessOutput(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString();
    }


    @GetMapping("/api/backup-all-json-public")
    public ResponseEntity<Resource> backupAllJsonFilesPublic(@RequestParam(value = "token", required = false) String token) throws IOException {
        System.out.println("Received JSON backup request with token: " + (token != null ? "provided" : "null"));
        if (token == null || !BACKUP_TOKEN.equals(token)) {
            System.out.println("Invalid or missing token for JSON backup");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        Path jsonDir = Paths.get(JSON_DIR);
        System.out.println("Checking JSON directory: " + jsonDir.toAbsolutePath());
        if (!Files.exists(jsonDir)) {
            System.out.println("JSON directory does not exist: " + jsonDir);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
        if (!Files.isDirectory(jsonDir)) {
            System.out.println("JSON path is not a directory: " + jsonDir);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        List<Path> jsonFiles = Files.list(jsonDir)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .collect(Collectors.toList());
        System.out.println("Found " + jsonFiles.size() + " JSON files: " + jsonFiles);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Path filePath : jsonFiles) {
                System.out.println("Adding JSON file: " + filePath);
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
    public ResponseEntity<Resource> backupAllImageFilesPublic(@RequestParam(value = "token", required = false) String token) throws IOException {
        System.out.println("Received image backup request with token: " + (token != null ? "provided" : "null"));
        if (token == null || !BACKUP_TOKEN.equals(token)) {
            System.out.println("Invalid or missing token for image backup");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

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
    }

    @GetMapping("/api/list-images-public")
    public ResponseEntity<List<String>> listImagesPublic(@RequestParam(value = "token", required = false) String token) throws IOException {
        System.out.println("Received list images request with token: " + (token != null ? "provided" : "null"));
        if (token == null || !BACKUP_TOKEN.equals(token)) {
            System.out.println("Invalid or missing token for list images");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

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
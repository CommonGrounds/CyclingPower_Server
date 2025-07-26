package dev.java4now;

import com.garmin.fit.FitRuntimeException;
import dev.java4now.db.CyclingActivityEntity;
import dev.java4now.db.CyclingActivityRepository;
import dev.java4now.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import jakarta.servlet.http.HttpServletRequest;

import dev.java4now.model.CyclingActivity;
import dev.java4now.service.FitFileDecoderService;

import java.time.Instant;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api")
//@CrossOrigin(origins = "*") // Allow all origins (adjust for production)
public class UserController {

    private static final ArrayList<User> users = new ArrayList<>();
    private static final String JSON_DIR = "json/"; // Directory to save JSON files on server
    private static final String UPLOAD_DIR = "uploads/"; // Directory to save .fit files on server
    private static final String IMAGE_DIR = "images/"; // New directory for images

    // IMPORTANT - curl http://localhost:8888/api/users - za proveru iz console bez browsera

    @Autowired
    private CyclingActivityRepository activityRepository;  // IMPORTANT SQLLite - dodatak

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final MyWebSocketHandler webSocketHandler;

    @Autowired // Инјектуј WebSocket handler
    public UserController(MyWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Autowired
    private ObjectMapper objectMapper; // Inject Jackson ObjectMapper


    @PostMapping("/endpoint")
    public ResponseEntity<String> createUser(@RequestBody User user) {
        System.out.println("Creating user: " + user.getName());
        try {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
            commitToGit("Add user " + user.getName());
            return ResponseEntity.ok("User created successfully!");
        } catch (Exception e) {
            System.err.println("Error creating user: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }



    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        System.out.println("Received ping request");
        return ResponseEntity.ok("OK");
    }



    @GetMapping("/users")
    public List<User> getUsers(HttpServletRequest request) {
        // Get client IP address
        String clientIp = request.getRemoteAddr(); // Simplified; use your getClientIpAddress() if needed
        System.out.println("Request from IP: " + clientIp);

        List<User> users = userRepository.findAll(); // Retrieve from database
        if (!users.isEmpty()) {
            return users;
        } else {
            return List.of(new User("---", "---", "---"));
        }
    }


    // New endpoint to check if username exists
    @GetMapping("/check-username")
    public ResponseEntity<Boolean> checkUsername(@RequestParam String username) {
//        System.out.println("userRepository.findByName(username): " + userRepository.findByName(username));
        Optional<User> user = userRepository.findByName(username);
//        System.out.println("user.isPresent(): " + user.isPresent());
        return ResponseEntity.ok(user.isPresent()); // Returns true if username exists, false otherwise
    }



    private String getClientIpAddress(HttpServletRequest request) {
        // Check for proxy headers (common in production environments)
        String ipAddress = request.getHeader("X-Forwarded-For");

        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        // Handle multiple IPs in X-Forwarded-For
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return ipAddress;
    }


    @Autowired
    private FitFileDecoderService fitFileDecoderService;

    @PostMapping("/upload-fit")
    public String uploadFitFile(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "User must be logged in to upload files.";
        }

        String username = authentication.getName(); // Get the logged-in user's name
        User user = userRepository.findByName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));  // IMPORTANT SQLLite - dodatak
        try {
            // Ensure upload directory exists
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

// Save the .fit file temporarily
            String originalFileName = file.getOriginalFilename();
            Path filePath = uploadPath.resolve(originalFileName);
            Files.write(filePath, file.getBytes());
            File fitFile = filePath.toFile();
            CyclingActivity activity = fitFileDecoderService.decodeFitFile(fitFile);
            String jsonFileName = username + "_" + originalFileName.replace(".fit", "") + "_" + System.currentTimeMillis() + ".json";
            Path jsonPath = Paths.get(JSON_DIR).resolve(jsonFileName);
            Files.createDirectories(jsonPath.getParent());

            // Serialize to JSON using ObjectMapper
//            String jsonContent = objectMapper.writeValueAsString(activity);
//            Files.writeString(jsonPath, jsonContent);

            // Write JSON file with explicit flush - umesto prethodnog
            try (FileOutputStream fos = new FileOutputStream(jsonPath.toFile())) {
                objectMapper.writeValue(fos, activity);
                fos.flush(); // Ensure data is written to disk
            }

            // Save to SQLite - // IMPORTANT SQLLite - dodatak
            CyclingActivityEntity dbActivity = new CyclingActivityEntity(user, jsonFileName);
            activityRepository.save(dbActivity);
            commitToGit("Add FIT and JSON for " + username, jsonPath.toString());

            Files.deleteIfExists(filePath);
            webSocketHandler.broadcast(jsonFileName);
            return "File processed successfully. JSON generated: " + jsonFileName;
        } catch (IOException | FitRuntimeException e) {
            return "Failed to process file: " + e.getMessage();
        }
    }


    @GetMapping("/download-json/{filename}")
    public ResponseEntity<Resource> downloadJsonFile(@PathVariable String filename,
                                                     Authentication authentication) throws IOException {
//------------------------------- TEST Authentication ---------------------------------
        /*
        System.out.println("Authentication: " + (authentication != null ? authentication.getName() : "null"));
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
         */
//--------------------------------------------------------------------------------------

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        String username = authentication.getName();
        if (!filename.startsWith(username + "_")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null); // User doesn’t own this file
        }

        Path filePath = Paths.get(JSON_DIR).resolve(filename).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (resource.exists() && resource.isReadable()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + resource.getFilename())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resource);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }


    @GetMapping("/list-json")
    public ResponseEntity<List<String>> listJsonFiles(Authentication authentication) {
        System.out.println("/list-json - Authentication: " + (authentication != null ? authentication.getName() : "null"));
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        String username = authentication.getName();
        // Use the new sorted repository method
        List<String> jsonFiles = activityRepository.findByUserNameOrderByUploadDateDesc(username)
                .stream()
                .map(CyclingActivityEntity::getFilename)
                .collect(Collectors.toList());

        return ResponseEntity.ok(jsonFiles);
    }

    /*
    // Sorting - old way
    @GetMapping("/list-json")
public ResponseEntity<List<String>> listJsonFiles(Authentication authentication) {
    System.out.println("/list-json - Authentication: " + (authentication != null ? authentication.getName() : "null"));
    if (authentication == null || !authentication.isAuthenticated()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
    }

    String username = authentication.getName();
    List<CyclingActivityEntity> activities = activityRepository.findByUserName(username);

    // Convert to List of filenames
    List<String> jsonFiles = new ArrayList<>();
    for (CyclingActivityEntity activity : activities) {
        jsonFiles.add(activity.getFilename());
    }

    // Sort using a custom comparator
    Collections.sort(jsonFiles, new Comparator<String>() {
        @Override
        public int compare(String filename1, String filename2) {
            long timestamp1 = extractTimestamp(filename1);
            long timestamp2 = extractTimestamp(filename2);
            // Sort in descending order (newest first)
            return Long.compare(timestamp2, timestamp1);
        }
    });

    return ResponseEntity.ok(jsonFiles);
}

// Helper method to extract timestamp from filename
private long extractTimestamp(String filename) {
    try {
        // Remove the .json extension
        String withoutExtension = filename.substring(0, filename.lastIndexOf('.'));
        // Get the last part after the last underscore
        String[] parts = withoutExtension.split("_");
        String timestampStr = parts[parts.length - 1];
        return Long.parseLong(timestampStr);
    } catch (Exception e) {
        return 0L; // Return 0 if parsing fails
    }
}
     */


    @PostMapping("/upload-image")
    public ResponseEntity<String> uploadImage(
            @RequestParam("file") MultipartFile imageFile,
            @RequestParam("jsonFile") String jsonFile, // The JSON file to associate with
            Authentication authentication) throws IOException {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User must be logged in to upload images.");
        }

        String username = authentication.getName();
        User user = userRepository.findByName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate jsonFile belongs to the user
        if (!jsonFile.startsWith(username + "_")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot upload image for a JSON file you don’t own.");
        }

        // Check if jsonFile exists in the database
        List<CyclingActivityEntity> userActivities = activityRepository.findByUserName(username);
        boolean jsonExists = userActivities.stream().anyMatch(a -> a.getFilename().equals(jsonFile));
        if (!jsonExists) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("JSON file not found: " + jsonFile);
        }

        // Ensure image directory exists
        Path imagePath = Paths.get(IMAGE_DIR);
        if (!Files.exists(imagePath)) {
            Files.createDirectories(imagePath);
        }

        // Generate image filename: username_jsonFileName_timestamp.extension
        String originalFileName = imageFile.getOriginalFilename();
        String extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        //       String imageFileName = username + "_" + jsonFile.replace(".json", "") + "_" + System.currentTimeMillis() + extension;
        String imageFileName = jsonFile.replace(".json", "") + "_" + System.currentTimeMillis() + extension;
        Path filePath = imagePath.resolve(imageFileName);

        // Save the image
        Files.write(filePath, imageFile.getBytes());
        commitToGit("Add image " + imageFileName);

        // Optionally broadcast via WebSocket
        webSocketHandler.broadcast("Image uploaded: " + imageFileName);

        return ResponseEntity.ok("Image uploaded successfully: " + imageFileName);
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
                pb.command("ls", "-la", "json", "images", "cycling_power.db");
                Process p = pb.start();
                System.out.println("Directory state: " + readProcessOutput(p));

                // Verify specific files exist
                for (String file : filesToAdd) {
                    if (Files.exists(Paths.get(file))) {
                        System.out.println("File exists: " + file);
                    } else {
                        System.err.println("File does not exist: " + file);
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
                for (String file : filesToAdd) {
                    addCommand.add(file);
                }
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
                    // Only pop stash if push succeeds and no further operations are needed
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


    @GetMapping("/image-for-json/{jsonFile}")
    public ResponseEntity<List<String>> getImageForJson(
            @PathVariable String jsonFile,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        String username = authentication.getName();
        if (!jsonFile.startsWith(username + "_")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        Path imageDir = Paths.get(IMAGE_DIR);
        List<String> imageFilenames = new ArrayList<>();

        // Check if directory exists before listing
        if (Files.exists(imageDir) && Files.isDirectory(imageDir)) {
            try {
                imageFilenames = Files.list(imageDir)
                        .filter(path -> path.getFileName().toString().startsWith(jsonFile.replace(".json", "") + "_"))
                        .sorted((p1, p2) -> {
                            String timestamp1 = p1.getFileName().toString().split("_")[3].replace(".jpg", "");
                            String timestamp2 = p2.getFileName().toString().split("_")[3].replace(".jpg", "");
                            return Long.compare(Long.parseLong(timestamp2), Long.parseLong(timestamp1));
                        })
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toList());
            } catch (IOException e) {
                System.err.println("Error listing images for " + jsonFile + ": " + e.getMessage());
                // Continue with empty list instead of throwing 500
            }
        }

        // Always return 200 OK with an empty list if no images
        List<String> signedUrls = imageFilenames.stream()
                .map(filename -> {
                    String token = generateToken(username);
                    return "https://cyclingpower-server-1.onrender.com/api/images/" + filename + "?token=" + token;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(signedUrls);
    }



    @GetMapping("/images/{imageFile}")
    public ResponseEntity<Resource> getImage(
            @PathVariable String imageFile,
            @RequestParam(required = false) String token,
            Authentication authentication) throws IOException {
        Path imagePath = Paths.get(IMAGE_DIR, imageFile);
        Resource resource = new UrlResource(imagePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            // No image directory yet, return empty list
            List<String> imageUrls = new ArrayList<>();
//            return ResponseEntity.ok(imageUrls);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        String username = imageFile.split("_")[0];
        if (authentication == null && token != null && validateToken(token, username)) {
            String contentType = Files.probeContentType(imagePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + resource.getFilename())
                    .contentType(MediaType.parseMediaType(contentType != null ? contentType : "image/jpeg"))
                    .body(resource);
        }

        if (authentication == null || !authentication.isAuthenticated() || !imageFile.startsWith(authentication.getName() + "_")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        String contentType = Files.probeContentType(imagePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + resource.getFilename())
                .contentType(MediaType.parseMediaType(contentType != null ? contentType : "image/jpeg"))
                .body(resource);
    }



    private String generateToken(String username) {
        String secret = "your-secret-key"; // Replace with a secure key
        String data = username + "|" + Instant.now().plusSeconds(3600).toEpochMilli();
        return Base64.getUrlEncoder().encodeToString((data + "|" + secret).getBytes());
    }



    private boolean validateToken(String token, String username) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token));
            String[] parts = decoded.split("\\|");
            if (parts.length != 3) return false;
            String tokenUsername = parts[0];
            long expiry = Long.parseLong(parts[1]);
            String secret = parts[2];
            return tokenUsername.equals(username) && expiry > Instant.now().toEpochMilli() && "your-secret-key".equals(secret);
        } catch (Exception e) {
            return false;
        }
    }
}

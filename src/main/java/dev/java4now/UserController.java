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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import dev.java4now.model.CyclingActivity;
import dev.java4now.service.FitFileDecoderService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api")
//@CrossOrigin(origins = "*") // Allow all origins (adjust for production)
public class UserController {

    private static final ArrayList<User> users = new ArrayList<>();
    private static final String JSON_DIR = "json/"; // Directory to save JSON files on server
    private static final String UPLOAD_DIR = "Uploads/"; // Directory to save .fit files on server
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

    @Autowired
    private FitFileDecoderService fitFileDecoderService;

    @Autowired
    private CloudStorageService cloudStorageService; // Inject the standalone service


    @PostMapping("/endpoint")
    public ResponseEntity<String> createUser(@RequestBody User user) {
        System.out.println("Creating user: " + user.getName());
        try {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepository.save(user);
            //commitToGit("Add user " + user.getName());
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



    @PostMapping("/upload-fit")
    public String uploadFitFile(@RequestParam("file") MultipartFile file, Authentication authentication) throws IOException {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "User must be logged in to upload files.";
        }

        String username = authentication.getName();
        User user = userRepository.findByName(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFileName = file.getOriginalFilename();
            Path filePath = uploadPath.resolve(originalFileName);
            Files.write(filePath, file.getBytes());
            File fitFile = filePath.toFile();
            CyclingActivity activity = fitFileDecoderService.decodeFitFile(fitFile);
            String jsonFileName = username + "_" + originalFileName.replace(".fit", "") + "_" + System.currentTimeMillis() + ".json";
            Path jsonPath = Paths.get(JSON_DIR).resolve(jsonFileName);
            Files.createDirectories(jsonPath.getParent());

            String jsonContent = objectMapper.writeValueAsString(activity);
            Files.writeString(jsonPath, jsonContent);

            // Upload JSON to Google Drive
            String jsonFileId = cloudStorageService.uploadFile(jsonPath, jsonFileName, "application/json");

            // Save to SQLite with Google Drive file ID and upload date
            CyclingActivityEntity dbActivity = new CyclingActivityEntity(user, jsonFileName);
            dbActivity.setGoogleDriveFileId(jsonFileId);
            dbActivity.setUploadDate(LocalDateTime.now()); // Explicitly set upload date
            activityRepository.save(dbActivity);

            Files.deleteIfExists(filePath);
            webSocketHandler.broadcast(jsonFileName);
            return "File processed successfully. JSON uploaded to Google Drive: " + jsonFileName;
        } catch (IOException | FitRuntimeException e) {
            return "Failed to process file: " + e.getMessage();
        }
    }


    @GetMapping("/download-json/{filename}")
    public ResponseEntity<Resource> downloadJsonFile(@PathVariable String filename, Authentication authentication) throws IOException {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        String username = authentication.getName();
        if (!filename.startsWith(username + "_")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        Optional<CyclingActivityEntity> activity = activityRepository.findByFilename(filename);
        if (activity.isEmpty() || activity.get().getGoogleDriveFileId() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        Path tempPath = Paths.get(JSON_DIR, filename);
        Files.createDirectories(tempPath.getParent());
        cloudStorageService.downloadFile(activity.get().getGoogleDriveFileId(), tempPath);

        Resource resource = new UrlResource(tempPath.toUri());
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
    public ResponseEntity<List<String>> listJsonFiles(Authentication authentication) throws IOException {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }

        String username = authentication.getName();
        List<CyclingActivityEntity> activities = activityRepository.findByUserNameOrderByUploadDateDesc(username);
        List<String> jsonFiles = activities.stream()
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
        //commitToGit("Add image " + imageFileName);

        // Optionally broadcast via WebSocket
        webSocketHandler.broadcast("Image uploaded: " + imageFileName);

        return ResponseEntity.ok("Image uploaded successfully: " + imageFileName);
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

package dev.java4now;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
public class GoogleDriveService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final String APPLICATION_NAME = "CyclingPower";
    private static final String FOLDER_ID = "1tE5TNeg7C_B0sc7tqec1BdtpR7nZDA6Q"; // Replace with your Google Drive folder ID
    private final Drive driveService;

    public GoogleDriveService() throws IOException, GeneralSecurityException {
        logger.info("Initializing GoogleDriveService");
        // Load service account credentials
        String credentialsJson = System.getenv("GOOGLE_DRIVE_CREDENTIALS");
        if (credentialsJson == null || credentialsJson.isEmpty()) {
            logger.error("GOOGLE_DRIVE_CREDENTIALS environment variable not set");
            throw new IOException("GOOGLE_DRIVE_CREDENTIALS environment variable not set");
        }
        try {
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(
                    new ByteArrayInputStream(credentialsJson.getBytes())
            );
            credentials = (ServiceAccountCredentials) credentials.createScoped(
                    Collections.singleton("https://www.googleapis.com/auth/drive.file")
            );

            // Initialize Drive API client
            driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials)
            ).setApplicationName(APPLICATION_NAME).build();
            logger.info("GoogleDriveService initialized successfully");
        } catch (IOException e) {
            logger.error("Failed to initialize GoogleDriveService: {}", e.getMessage(), e);
            throw e;
        } catch (GeneralSecurityException e) {
            logger.error("Security error initializing GoogleDriveService: {}", e.getMessage(), e);
            throw e;
        }
    }

    public String uploadFile(Path localPath, String fileName, String mimeType) throws IOException {
        logger.info("Uploading file {} to Google Drive", fileName);
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList(FOLDER_ID));

        try {
            File uploadedFile = driveService.files().create(
                    fileMetadata,
                    new com.google.api.client.http.FileContent(mimeType, localPath.toFile())
            ).setFields("id").execute();
            logger.info("Uploaded file {} with ID {}", fileName, uploadedFile.getId());
            return uploadedFile.getId();
        } catch (IOException e) {
            logger.error("Failed to upload file {}: {}", fileName, e.getMessage(), e);
            throw e;
        }
    }

    public java.io.File downloadFile(String fileId, Path destination) throws IOException {
        logger.info("Downloading file with ID {} to {}", fileId, destination);
        try {
            Files.createDirectories(destination.getParent());
            try (var outputStream = Files.newOutputStream(destination)) {
                driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            }
            logger.info("Downloaded file with ID {} to {}", fileId, destination);
            return destination.toFile();
        } catch (IOException e) {
            logger.error("Failed to download file with ID {}: {}", fileId, e.getMessage(), e);
            throw e;
        }
    }
}
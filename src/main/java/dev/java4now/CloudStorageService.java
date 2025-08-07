package dev.java4now;

import nz.mega.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Service
public class CloudStorageService {

    private static final Logger logger = LoggerFactory.getLogger(CloudStorageService.class);
    private static final String MEGA_EMAIL = System.getenv("MEGA_EMAIL");
    private static final String MEGA_PASSWORD = System.getenv("MEGA_PASSWORD");
    private static final String FOLDER_NAME = "CyclingPowerUploads";
    private MegaApi megaApi;

    public CloudStorageService() {
        logger.info("Initializing CloudStorageService with MEGA");
        if (MEGA_EMAIL == null || MEGA_PASSWORD == null) {
            logger.warn("MEGA_EMAIL or MEGA_PASSWORD not set. Cloud storage disabled.");
            this.megaApi = null;
            return;
        }
        try {
            System.out.println("java.library.path: " + System.getProperty("java.library.path"));
            System.loadLibrary("mega");
            this.megaApi = new MegaApi(null, "CyclingPowerServer");
            CompletableFuture<MegaError> loginFuture = new CompletableFuture<>();
            megaApi.login(MEGA_EMAIL, MEGA_PASSWORD, new MegaRequestListener() {
                @Override
                public void onRequestFinish(MegaApi api, MegaRequest request, MegaError error) {
                    if (error.getErrorCode() == MegaError.API_OK) {
                        logger.info("MEGA login successful");
                        loginFuture.complete(error);
                    } else {
                        logger.error("MEGA login failed: {}", error.getErrorString());
                        loginFuture.completeExceptionally(new IOException("MEGA login failed: " + error.getErrorString()));
                    }
                }
            });
            System.out.println("MEGA login successful");
            loginFuture.get();
            ensureFolderExists();
        } catch (UnsatisfiedLinkError | Exception e) {
            logger.warn("Failed to initialize MEGA. Cloud storage disabled: {}", e.getMessage());
            this.megaApi = null;
        }
    }

    private void ensureFolderExists() throws IOException {
        if (megaApi == null) {
            logger.warn("MEGA not initialized. Skipping folder creation.");
            return;
        }
        MegaNode root = megaApi.getRootNode();
        MegaNode folder = megaApi.getNodeByPath("/" + FOLDER_NAME, root);
        if (folder == null) {
            CompletableFuture<MegaNode> createFolderFuture = new CompletableFuture<>();
            megaApi.createFolder(FOLDER_NAME, root, new MegaRequestListener() {
                @Override
                public void onRequestFinish(MegaApi api, MegaRequest request, MegaError error) {
                    if (error.getErrorCode() == MegaError.API_OK) {
                        createFolderFuture.complete(megaApi.getNodeByPath("/" + FOLDER_NAME, root));
                    } else {
                        createFolderFuture.completeExceptionally(new IOException("Failed to create folder: " + error.getErrorString()));
                    }
                }
            });
            try {
                createFolderFuture.get();
                logger.info("Created MEGA folder: {}", FOLDER_NAME);
            } catch (Exception e) {
                logger.error("Failed to create MEGA folder: {}", e.getMessage(), e);
                throw new IOException("Failed to create MEGA folder", e);
            }
        }
    }

    public String uploadFile(Path localPath, String fileName, String mimeType) throws IOException {
        if (megaApi == null) {
            logger.warn("Cloud storage is disabled. Skipping upload for {}", fileName);
            return null; // Store locally or handle differently
        }
        logger.info("Uploading file {} to MEGA", fileName);
        MegaNode parent = megaApi.getNodeByPath("/" + FOLDER_NAME);
        CompletableFuture<MegaNode> uploadFuture = new CompletableFuture<>();
        megaApi.startUpload(localPath.toString(), parent, fileName, 0, null, false, false, null, new MegaTransferListener() {
            @Override
            public void onTransferFinish(MegaApi api, MegaTransfer transfer, MegaError error) {
                if (error.getErrorCode() == MegaError.API_OK) {
                    MegaNode node = megaApi.getNodeByPath("/" + FOLDER_NAME + "/" + fileName);
                    uploadFuture.complete(node);
                    logger.info("Uploaded file {} with handle {}", fileName, node.getHandle());
                } else {
                    uploadFuture.completeExceptionally(new IOException("Upload failed: " + error.getErrorString()));
                }
            }
        });
        try {
            MegaNode node = uploadFuture.get();
            return String.valueOf(node.getHandle());
        } catch (Exception e) {
            logger.error("Failed to upload file {}: {}", fileName, e.getMessage(), e);
            throw new IOException("Failed to upload to MEGA", e);
        }
    }

    public java.io.File downloadFile(String fileId, Path destination) throws IOException {
        if (megaApi == null) {
            logger.warn("Cloud storage is disabled. Cannot download file with ID {}", fileId);
            throw new IOException("Cloud storage is disabled");
        }
        logger.info("Downloading file with ID {} to {}", fileId, destination);
        MegaNode node = megaApi.getNodeByHandle(Long.parseLong(fileId));
        if (node == null) {
            throw new IOException("File not found: " + fileId);
        }
        CompletableFuture<MegaError> downloadFuture = new CompletableFuture<>();
        try {
            Files.createDirectories(destination.getParent());
            megaApi.startDownload(node, destination.toString(), null, null, false, null, 0, 0, false, new MegaTransferListener() {
                @Override
                public void onTransferFinish(MegaApi api, MegaTransfer transfer, MegaError error) {
                    if (error.getErrorCode() == MegaError.API_OK) {
                        downloadFuture.complete(error);
                    } else {
                        downloadFuture.completeExceptionally(new IOException("Download failed: " + error.getErrorString()));
                    }
                }
            });
            downloadFuture.get();
            logger.info("Downloaded file with ID {} to {}", fileId, destination);
            return destination.toFile();
        } catch (Exception e) {
            logger.error("Failed to download file with ID {}: {}", fileId, e.getMessage(), e);
            throw new IOException("Failed to download from MEGA", e);
        }
    }
}
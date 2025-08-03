package dev.java4now.db;

import dev.java4now.model.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class CyclingActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String filename;

    private String googleDriveFileId;

    @Column(name = "upload_date")
    private LocalDateTime uploadDate; // New field for upload timestamp

    public CyclingActivityEntity() {
    }

    public CyclingActivityEntity(User user, String filename) {
        this.user = user;
        this.filename = filename;
        this.uploadDate = LocalDateTime.now(); // Set upload date to current time
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getGoogleDriveFileId() {
        return googleDriveFileId;
    }

    public void setGoogleDriveFileId(String googleDriveFileId) {
        this.googleDriveFileId = googleDriveFileId;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }
}
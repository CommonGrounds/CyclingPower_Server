package dev.java4now.db;

import dev.java4now.model.User;
import jakarta.persistence.*;

// IMPORTANT SQLLite

@Entity
@Table(name = "CYCLING_ACTIVITY")
public class CyclingActivityEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "USER_ID")
    private User user;

    @Column(name = "FILENAME", nullable = false)
    private String filename;

    @Column(name = "UPLOAD_DATE")
    private Long uploadDate; // Change to Long for milliseconds

    // Constructors
    public CyclingActivityEntity() {}

    public CyclingActivityEntity(User user, String filename) {
        this.user = user;
        this.filename = filename;
        this.uploadDate = System.currentTimeMillis(); // Set as Unix timestamp
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public Long getUploadDate() { return uploadDate; }
    public void setUploadDate(Long uploadDate) { this.uploadDate = uploadDate; }
}
package com.xiaoc.workbench.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String projectId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private Instant createdAt;

    protected Room() {
    }

    public Room(String id, String projectId, String name) {
        this.id = id;
        this.projectId = projectId;
        this.name = name;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}

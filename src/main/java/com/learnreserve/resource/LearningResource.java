package com.learnreserve.resource;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "learning_resources")
public class LearningResource {
    public enum Type { STUDY_ROOM, MENTORING, WORKSHOP }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 2000)
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Type type;
    @Column(nullable = false, length = 200)
    private String location;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private Instant createdAt;

    protected LearningResource() {}

    public LearningResource(String name, String description, Type type, String location, Instant now) {
        update(name, description, type, location);
        this.createdAt = now;
    }

    public void update(String name, String description, Type type, String location) {
        this.name = name.strip();
        this.description = description.strip();
        this.type = type;
        this.location = location.strip();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Type getType() { return type; }
    public String getLocation() { return location; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

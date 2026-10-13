package com.learnreserve.availability;

import com.learnreserve.resource.LearningResource;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "availability_slots")
public class AvailabilitySlot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "resource_id")
    private LearningResource resource;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant endsAt;
    @Column(nullable = false)
    private boolean active = true;

    protected AvailabilitySlot() {}
    public AvailabilitySlot(LearningResource resource, Instant startsAt, Instant endsAt) {
        this.resource = resource;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }
    public Long getId() { return id; }
    public LearningResource getResource() { return resource; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public void close() { active = false; }
}

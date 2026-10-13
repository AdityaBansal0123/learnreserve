package com.learnreserve.booking;

import com.learnreserve.auth.User;
import com.learnreserve.availability.AvailabilitySlot;
import com.learnreserve.resource.LearningResource;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {
    public enum Status { CONFIRMED, CANCELLED, COMPLETED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "learner_id")
    private User learner;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "resource_id")
    private LearningResource resource;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "slot_id")
    private AvailabilitySlot slot;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant endsAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.CONFIRMED;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant cancelledAt;
    private Instant completedAt;

    protected Booking() {}
    public Booking(User learner, LearningResource resource, AvailabilitySlot slot, Instant startsAt, Instant endsAt, Instant now) {
        this.learner = learner;
        this.resource = resource;
        this.slot = slot;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.createdAt = now;
    }

    public void cancel(Instant now) { status = Status.CANCELLED; cancelledAt = now; }
    public Long getId() { return id; }
    public User getLearner() { return learner; }
    public LearningResource getResource() { return resource; }
    public AvailabilitySlot getSlot() { return slot; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCompletedAt() { return completedAt; }
}

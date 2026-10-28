package com.spatialstay.hoteloperations;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.user.UserRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hotel_audit_events", indexes = {
        @Index(name = "idx_hotel_audit_hotel_time", columnList = "hotel_id, occurred_at"),
        @Index(name = "idx_hotel_audit_actor", columnList = "actor_public_id")
})
public class HotelAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;
    @Column(name = "actor_public_id", nullable = false, updatable = false)
    private UUID actorPublicId;
    @Column(name = "actor_role", nullable = false, length = 32, updatable = false)
    @Enumerated(EnumType.STRING)
    private UserRole actorRole;
    @Column(nullable = false, length = 80, updatable = false)
    private String action;
    @Column(name = "resource_type", nullable = false, length = 50, updatable = false)
    private String resourceType;
    @Column(name = "resource_id", length = 120, updatable = false)
    private String resourceId;
    @Column(length = 1000, updatable = false)
    private String summary;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected HotelAuditEvent() {}

    public HotelAuditEvent(Hotel hotel, UUID actorPublicId, UserRole actorRole, String action,
                           String resourceType, String resourceId, String summary, Instant occurredAt) {
        this.hotel = hotel; this.actorPublicId = actorPublicId; this.actorRole = actorRole;
        this.action = action; this.resourceType = resourceType; this.resourceId = resourceId;
        this.summary = summary; this.occurredAt = occurredAt;
    }
    public Long getId(){ return id; } public Hotel getHotel(){ return hotel; }
    public UUID getActorPublicId(){ return actorPublicId; } public UserRole getActorRole(){ return actorRole; }
    public String getAction(){ return action; } public String getResourceType(){ return resourceType; }
    public String getResourceId(){ return resourceId; } public String getSummary(){ return summary; }
    public Instant getOccurredAt(){ return occurredAt; }
}

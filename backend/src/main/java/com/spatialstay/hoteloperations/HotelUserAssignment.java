package com.spatialstay.hoteloperations;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "hotel_user_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hotel_user_assignment_user_hotel",
                columnNames = {"user_id", "hotel_id"}
        ),
        indexes = {
                @Index(name = "idx_hotel_user_assignment_user_active", columnList = "user_id,active"),
                @Index(name = "idx_hotel_user_assignment_hotel_active", columnList = "hotel_id,active")
        }
)
public class HotelUserAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HotelUserAssignment() {
    }

    public HotelUserAssignment(User user, Hotel hotel) {
        this.user = user;
        this.hotel = hotel;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Hotel getHotel() { return hotel; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }
}

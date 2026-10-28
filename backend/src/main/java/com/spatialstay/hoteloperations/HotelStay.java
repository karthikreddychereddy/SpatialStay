package com.spatialstay.hoteloperations;

import com.spatialstay.booking.Booking;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hotel_stays", uniqueConstraints = @UniqueConstraint(name = "uk_hotel_stay_booking", columnNames = "booking_id"), indexes = {
        @Index(name = "idx_hotel_stay_status", columnList = "status"),
        @Index(name = "idx_hotel_stay_booking", columnList = "booking_id", unique = true)
})
public class HotelStay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true, updatable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StayStatus status = StayStatus.NOT_CHECKED_IN;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_in_by", columnDefinition = "uuid")
    private UUID checkedInBy;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Column(name = "checked_out_by", columnDefinition = "uuid")
    private UUID checkedOutBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HotelStay() {}

    public static HotelStay forBooking(Booking booking) {
        if (booking == null) throw new IllegalArgumentException("Booking is required.");
        HotelStay stay = new HotelStay();
        stay.booking = booking;
        return stay;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = StayStatus.NOT_CHECKED_IN;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public void checkIn(Instant at, UUID operatorId) {
        if (status != StayStatus.NOT_CHECKED_IN) {
            throw new IllegalStateException("Stay cannot be checked in from status " + status + ".");
        }
        status = StayStatus.CHECKED_IN;
        checkedInAt = requireInstant(at);
        checkedInBy = requireOperator(operatorId);
    }

    public void checkOut(Instant at, UUID operatorId) {
        if (status != StayStatus.CHECKED_IN) {
            throw new IllegalStateException("Stay cannot be checked out from status " + status + ".");
        }
        status = StayStatus.CHECKED_OUT;
        checkedOutAt = requireInstant(at);
        checkedOutBy = requireOperator(operatorId);
    }

    private static Instant requireInstant(Instant value) {
        if (value == null) throw new IllegalArgumentException("Operation time is required.");
        return value;
    }

    private static UUID requireOperator(UUID value) {
        if (value == null) throw new IllegalArgumentException("Operator identity is required.");
        return value;
    }

    public Long getId() { return id; }
    public Booking getBooking() { return booking; }
    public StayStatus getStatus() { return status; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public UUID getCheckedInBy() { return checkedInBy; }
    public Instant getCheckedOutAt() { return checkedOutAt; }
    public UUID getCheckedOutBy() { return checkedOutBy; }
}

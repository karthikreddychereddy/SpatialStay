package com.spatialstay.room;

import com.spatialstay.hotel.Hotel;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "rooms",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_room_hotel_slug",
                        columnNames = {
                                "hotel_id",
                                "slug"
                        }
                )
        }
)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "hotel_id",
            nullable = false
    )
    private Hotel hotel;

    @Column(
            nullable = false,
            length = 120
    )
    private String slug;

    @Column(
            nullable = false,
            length = 200
    )
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(
            nullable = false,
            length = 80
    )
    private String roomType;

    @Column(nullable = false)
    private Integer maxGuests;

    @Column(
            name = "total_units",
            nullable = false
    )
    private Integer totalUnits = 1;

    @Column(
            nullable = false,
            length = 100
    )
    private String bedType;

    private Integer roomSizeSqFt;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal pricePerNight;

    @Column(
            nullable = false,
            length = 3
    )
    private String currency = "INR";

    @Column(length = 1000)
    private String heroImageUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "room_images",
            joinColumns = @JoinColumn(name = "room_id")
    )
    @Column(
            name = "image_url",
            nullable = false,
            length = 1000
    )
    @OrderColumn(name = "sort_order")
    private List<String> imageUrls = new ArrayList<>();

    @Column(nullable = false)
    private Boolean spatialAvailable = false;

    @Column(
            nullable = false,
            length = 30
    )
    private String status = "ACTIVE";

    @ManyToMany
    @JoinTable(
            name = "room_amenities",
            joinColumns =
                    @JoinColumn(name = "room_id"),
            inverseJoinColumns =
                    @JoinColumn(name = "amenity_id")
    )
    private Set<Amenity> amenities =
            new LinkedHashSet<>();

    @Column(
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Room() {
        // Required by JPA
    }

    public static Room createForAdminOnboarding() {
        return new Room();
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now =
                LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (totalUnits == null) {
            totalUnits = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public void setHotel(Hotel hotel) {
        this.hotel = hotel;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(
            String description
    ) {
        this.description = description;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(
            String roomType
    ) {
        this.roomType = roomType;
    }

    public Integer getMaxGuests() {
        return maxGuests;
    }

    public void setMaxGuests(
            Integer maxGuests
    ) {
        this.maxGuests = maxGuests;
    }

    public Integer getTotalUnits() {
        return totalUnits;
    }

    public void setTotalUnits(
            Integer totalUnits
    ) {
        if (
                totalUnits == null ||
                totalUnits < 0
        ) {
            throw new IllegalArgumentException(
                    "Total room units cannot be negative."
            );
        }

        this.totalUnits = totalUnits;
    }

    public String getBedType() {
        return bedType;
    }

    public void setBedType(
            String bedType
    ) {
        this.bedType = bedType;
    }

    public Integer getRoomSizeSqFt() {
        return roomSizeSqFt;
    }

    public void setRoomSizeSqFt(
            Integer roomSizeSqFt
    ) {
        this.roomSizeSqFt = roomSizeSqFt;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public void setPricePerNight(
            BigDecimal pricePerNight
    ) {
        this.pricePerNight = pricePerNight;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(
            String currency
    ) {
        this.currency = currency;
    }

    public String getHeroImageUrl() {
        return heroImageUrl;
    }

    public void setHeroImageUrl(
            String heroImageUrl
    ) {
        this.heroImageUrl = heroImageUrl;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(
            List<String> imageUrls
    ) {
        this.imageUrls =
                imageUrls != null
                        ? new ArrayList<>(imageUrls)
                        : new ArrayList<>();
    }

    public Boolean getSpatialAvailable() {
        return spatialAvailable;
    }

    public void setSpatialAvailable(
            Boolean spatialAvailable
    ) {
        this.spatialAvailable =
                spatialAvailable;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(
            String status
    ) {
        this.status = status;
    }

    public Set<Amenity> getAmenities() {
        return amenities;
    }

    public void setAmenities(
            Set<Amenity> amenities
    ) {
        this.amenities =
                amenities != null
                        ? amenities
                        : new LinkedHashSet<>();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
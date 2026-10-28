package com.spatialstay.admin;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity
@Table(name="hotel_applications", indexes={@Index(name="idx_hotel_app_status_created",columnList="status,created_at")})
public class HotelApplication {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="applicant_user_id",nullable=false) private User applicant;
 @Column(nullable=false,length=40) private String ownerPhone;
 @Column(nullable=false,length=200) private String hotelName;
 @Column(nullable=false,length=80) private String propertyType;
 private Integer starCategory;
 @Column(nullable=false,length=120) private String city; @Column(length=120) private String state; @Column(nullable=false,length=120) private String country;
 @Column(nullable=false,length=500) private String address; @Column(nullable=false,length=40) private String contactPhone; @Column(nullable=false,length=254) private String contactEmail;
 @Column(length=300) private String website; @Column(nullable=false,precision=12,scale=2) private BigDecimal startingPrice; @Column(columnDefinition="TEXT") private String description; private Integer totalRooms;
 private LocalTime checkInTime; private LocalTime checkOutTime;
 @Column(columnDefinition="TEXT") private String houseRules; @Column(columnDefinition="TEXT") private String childPolicy; @Column(columnDefinition="TEXT") private String petPolicy; @Column(columnDefinition="TEXT") private String smokingPolicy;
 @ElementCollection @CollectionTable(name="hotel_application_amenities",joinColumns=@JoinColumn(name="application_id")) @Column(name="amenity",nullable=false,length=120) private Set<String> amenities=new LinkedHashSet<>();
 @ElementCollection @CollectionTable(name="hotel_application_images",joinColumns=@JoinColumn(name="application_id")) @Column(name="image_url",nullable=false,length=1000) @OrderColumn(name="sort_order") private List<String> hotelImageUrls=new ArrayList<>();
 @OneToMany(mappedBy="application",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("id asc") private List<HotelApplicationRoom> rooms=new ArrayList<>();
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private HotelApplicationStatus status=HotelApplicationStatus.PENDING_APPROVAL;
 @Column(columnDefinition="TEXT") private String adminNote; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="approved_hotel_id") private Hotel approvedHotel;
 @Column(nullable=false,updatable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt; private Instant decidedAt;
 protected HotelApplication(){}
 public HotelApplication(User applicant,CreateHotelApplicationRequest r){this.applicant=applicant;ownerPhone=r.ownerPhone().trim();hotelName=r.hotelName().trim();propertyType=r.propertyType().trim();starCategory=r.starCategory();city=r.city().trim();state=clean(r.state());country=r.country().trim();address=r.address().trim();contactPhone=r.contactPhone().trim();contactEmail=r.contactEmail().trim().toLowerCase(Locale.ROOT);website=clean(r.website());startingPrice=r.startingPrice();description=clean(r.description());totalRooms=r.totalRooms();checkInTime=r.checkInTime();checkOutTime=r.checkOutTime();houseRules=clean(r.houseRules());childPolicy=clean(r.childPolicy());petPolicy=clean(r.petPolicy());smokingPolicy=clean(r.smokingPolicy());if(r.amenities()!=null) amenities.addAll(r.amenities());hotelImageUrls.addAll(r.hotelImageUrls());for(var room:r.rooms())rooms.add(new HotelApplicationRoom(this,room));}
 private String clean(String v){return v==null||v.isBlank()?null:v.trim();}
 @PrePersist void create(){createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void update(){updatedAt=Instant.now();}
 public void approve(Hotel hotel,String note){status=HotelApplicationStatus.APPROVED;approvedHotel=hotel;adminNote=note;decidedAt=Instant.now();} public void reject(String note){status=HotelApplicationStatus.REJECTED;adminNote=note;decidedAt=Instant.now();}
 public Long getId(){return id;} public User getApplicant(){return applicant;} public String getOwnerPhone(){return ownerPhone;} public String getHotelName(){return hotelName;} public String getPropertyType(){return propertyType;} public Integer getStarCategory(){return starCategory;} public String getCity(){return city;} public String getState(){return state;} public String getCountry(){return country;} public String getAddress(){return address;} public String getContactPhone(){return contactPhone;} public String getContactEmail(){return contactEmail;} public String getWebsite(){return website;} public BigDecimal getStartingPrice(){return startingPrice;} public String getDescription(){return description;} public Integer getTotalRooms(){return totalRooms;} public LocalTime getCheckInTime(){return checkInTime;} public LocalTime getCheckOutTime(){return checkOutTime;} public String getHouseRules(){return houseRules;} public String getChildPolicy(){return childPolicy;} public String getPetPolicy(){return petPolicy;} public String getSmokingPolicy(){return smokingPolicy;} public Set<String> getAmenities(){return amenities;} public List<String> getHotelImageUrls(){return hotelImageUrls;} public List<HotelApplicationRoom> getRooms(){return rooms;} public HotelApplicationStatus getStatus(){return status;} public String getAdminNote(){return adminNote;} public Hotel getApprovedHotel(){return approvedHotel;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Instant getDecidedAt(){return decidedAt;}
}

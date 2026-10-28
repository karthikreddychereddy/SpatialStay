package com.spatialstay.admin;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="hotel_application_rooms")
public class HotelApplicationRoom {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="application_id",nullable=false) private HotelApplication application;
    @Column(nullable=false,length=200) private String name;
    @Column(nullable=false,length=80) private String roomType;
    @Column(columnDefinition="TEXT") private String description;
    @Column(nullable=false) private Integer maxGuests;
    @Column(nullable=false) private Integer totalUnits;
    @Column(nullable=false,length=100) private String bedType;
    private Integer roomSizeSqFt;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal pricePerNight;
    @ElementCollection @CollectionTable(name="hotel_application_room_images",joinColumns=@JoinColumn(name="application_room_id"))
    @Column(name="image_url",nullable=false,length=1000) @OrderColumn(name="sort_order") private List<String> imageUrls=new ArrayList<>();
    protected HotelApplicationRoom(){}
    public HotelApplicationRoom(HotelApplication application, CreateHotelApplicationRequest.ProposedRoom r){this.application=application;name=r.name().trim();roomType=r.roomType().trim();description=r.description();maxGuests=r.maxGuests();totalUnits=r.totalUnits();bedType=r.bedType().trim();roomSizeSqFt=r.roomSizeSqFt();pricePerNight=r.pricePerNight();imageUrls=new ArrayList<>(r.imageUrls());}
    public Long getId(){return id;} public String getName(){return name;} public String getRoomType(){return roomType;} public String getDescription(){return description;} public Integer getMaxGuests(){return maxGuests;} public Integer getTotalUnits(){return totalUnits;} public String getBedType(){return bedType;} public Integer getRoomSizeSqFt(){return roomSizeSqFt;} public BigDecimal getPricePerNight(){return pricePerNight;} public List<String> getImageUrls(){return imageUrls;}
}

package com.spatialstay.admin;
import java.math.BigDecimal; import java.time.*; import java.util.*;
public record HotelApplicationResponse(Long id,String reference,UUID applicantPublicId,String applicantName,String applicantEmail,String ownerPhone,String hotelName,String propertyType,Integer starCategory,String city,String state,String country,String address,String contactPhone,String contactEmail,String website,BigDecimal startingPrice,String description,Integer totalRooms,LocalTime checkInTime,LocalTime checkOutTime,String houseRules,String childPolicy,String petPolicy,String smokingPolicy,Set<String> amenities,List<String> hotelImageUrls,List<RoomProposal> rooms,HotelApplicationStatus status,String adminNote,Long approvedHotelId,Instant createdAt,Instant decidedAt){
 public record RoomProposal(Long id,String name,String roomType,String description,Integer maxGuests,Integer totalUnits,String bedType,Integer roomSizeSqFt,BigDecimal pricePerNight,List<String> imageUrls){}
}

package com.spatialstay.hoteloperations;

public record CreateHotelStaffRequest(
        String email,
        String password,
        String firstName,
        String lastName
) {}

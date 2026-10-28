package com.spatialstay.hoteloperations;

import java.time.LocalTime;

public record UpdateHotelPoliciesRequest(
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String checkInInstructions,
        String houseRules,
        String childPolicy,
        String petPolicy,
        String smokingPolicy
) {}

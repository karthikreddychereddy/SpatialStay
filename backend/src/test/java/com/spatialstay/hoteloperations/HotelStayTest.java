package com.spatialstay.hoteloperations;

import com.spatialstay.booking.Booking;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class HotelStayTest {
    @Test
    void validStayLifecycleIsOneWay() {
        HotelStay stay = HotelStay.forBooking(mock(Booking.class));
        UUID operator = UUID.randomUUID();
        Instant checkIn = Instant.parse("2026-10-09T10:00:00Z");
        Instant checkOut = Instant.parse("2026-10-10T08:00:00Z");

        stay.checkIn(checkIn, operator);
        assertEquals(StayStatus.CHECKED_IN, stay.getStatus());
        assertEquals(checkIn, stay.getCheckedInAt());

        assertThrows(IllegalStateException.class, () -> stay.checkIn(checkIn, operator));

        stay.checkOut(checkOut, operator);
        assertEquals(StayStatus.CHECKED_OUT, stay.getStatus());
        assertEquals(checkOut, stay.getCheckedOutAt());

        assertThrows(IllegalStateException.class, () -> stay.checkOut(checkOut, operator));
    }

    @Test
    void checkoutBeforeCheckinIsRejected() {
        HotelStay stay = HotelStay.forBooking(mock(Booking.class));
        assertThrows(IllegalStateException.class,
                () -> stay.checkOut(Instant.now(), UUID.randomUUID()));
    }
}

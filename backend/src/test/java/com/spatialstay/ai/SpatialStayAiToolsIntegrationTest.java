package com.spatialstay.ai;

import com.spatialstay.hotel.HotelResponse;
import com.spatialstay.room.RoomAvailabilityResponse;
import com.spatialstay.room.RoomResponse;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
                "spring.sql.init.mode=always",
                "spring.jpa.defer-datasource-initialization=true",
                "app.ai.enabled=false",
                "spring.ai.model.chat=none",
                "spring.ai.model.embedding=none"
        }
)
class SpatialStayAiToolsIntegrationTest {

    @Autowired
    private SpatialStayAiTools tools;

    @Test
    void searchHotelsReturnsAuthoritativeHyderabadHotel() {
        List<HotelResponse> hotels =
                tools.searchHotels(
                        "Hyderabad",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "recommended",
                        null,
                        null,
                        null
                );

        assertThat(hotels)
                .hasSize(1);

        HotelResponse hotel =
                hotels.getFirst();

        assertThat(hotel.slug())
                .isEqualTo(
                        "skyline-grand-hyderabad"
                );

        assertThat(hotel.name())
                .isEqualTo(
                        "Skyline Grand"
                );

        assertThat(hotel.city())
                .isEqualTo(
                        "Hyderabad"
                );

        assertThat(hotel.rating())
                .isEqualByComparingTo(
                        "4.6"
                );

        assertThat(hotel.startingPrice())
                .isEqualByComparingTo(
                        "7200.00"
                );

        assertThat(hotel.verified())
                .isTrue();

        assertThat(hotel.status())
                .isEqualToIgnoringCase(
                        "ACTIVE"
                );
    }

    @Test
    void searchHotelsReturnsEmptyForUnknownDestination() {
        List<HotelResponse> hotels =
                tools.searchHotels(
                        "Paris",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "recommended",
                        null,
                        null,
                        null
                );

        assertThat(hotels)
                .isEmpty();
    }

    @Test
    void getHotelDetailsReturnsExactHotel() {
        HotelResponse hotel =
                tools.getHotelDetails(
                        "skyline-grand-hyderabad"
                );

        assertThat(hotel)
                .isNotNull();

        assertThat(hotel.slug())
                .isEqualTo(
                        "skyline-grand-hyderabad"
                );

        assertThat(hotel.name())
                .isEqualTo(
                        "Skyline Grand"
                );

        assertThat(hotel.city())
                .isEqualTo(
                        "Hyderabad"
                );
    }

    @Test
    void getHotelDetailsReturnsNullForUnknownSlug() {
        HotelResponse hotel =
                tools.getHotelDetails(
                        "royal-galaxy-palace-paris"
                );

        assertThat(hotel)
                .isNull();
    }

    @Test
    void getHotelRoomsReturnsAuthoritativeRooms() {
        List<RoomResponse> rooms =
                tools.getHotelRooms(
                        "skyline-grand-hyderabad"
                );

        assertThat(rooms)
                .hasSize(2);

        assertThat(rooms)
                .extracting(
                        RoomResponse::slug
                )
                .containsExactlyInAnyOrder(
                        "executive-king",
                        "skyline-suite"
                );

        RoomResponse executiveKing =
                rooms.stream()
                        .filter(
                                room ->
                                        "executive-king"
                                                .equals(
                                                        room.slug()
                                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                executiveKing.name()
        ).isEqualTo(
                "Executive King"
        );

        assertThat(
                executiveKing.maxGuests()
        ).isEqualTo(2);

        assertThat(
                executiveKing.pricePerNight()
        ).isEqualByComparingTo(
                "7800.00"
        );

        assertThat(
                executiveKing.currency()
        ).isEqualTo(
                "INR"
        );
    }

    @Test
    void availabilityUsesBackendInventoryAndStayTotal() {
        LocalDate checkIn =
                LocalDate.now()
                        .plusDays(30);

        LocalDate checkOut =
                checkIn.plusDays(3);

        List<RoomAvailabilityResponse> availability =
                tools.checkRoomAvailability(
                        "skyline-grand-hyderabad",
                        checkIn,
                        checkOut,
                        2
                );

        assertThat(availability)
                .hasSize(2);

        RoomAvailabilityResponse executiveKing =
                availability.stream()
                        .filter(
                                room ->
                                        "executive-king"
                                                .equals(
                                                        room.roomSlug()
                                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                executiveKing.nights()
        ).isEqualTo(3);

        assertThat(
                executiveKing.guests()
        ).isEqualTo(2);

        assertThat(
                executiveKing.maxGuests()
        ).isEqualTo(2);

        assertThat(
                executiveKing.totalUnits()
        ).isEqualTo(10);

        assertThat(
                executiveKing.availableUnits()
        ).isEqualTo(10);

        assertThat(
                executiveKing.available()
        ).isTrue();

        assertThat(
                executiveKing.pricePerNight()
        ).isEqualByComparingTo(
                "7800.00"
        );

        assertThat(
                executiveKing.estimatedStayTotal()
        ).isEqualByComparingTo(
                new BigDecimal(
                        "23400.00"
                )
        );

        assertThat(
                executiveKing.currency()
        ).isEqualTo(
                "INR"
        );
    }

    @Test
    void availabilityFiltersRoomsThatCannotFitGuestCount() {
        LocalDate checkIn =
                LocalDate.now()
                        .plusDays(30);

        LocalDate checkOut =
                checkIn.plusDays(2);

        List<RoomAvailabilityResponse> availability =
                tools.checkRoomAvailability(
                        "skyline-grand-hyderabad",
                        checkIn,
                        checkOut,
                        3
                );

        assertThat(availability)
                .hasSize(1);

        assertThat(
                availability.getFirst()
                        .roomSlug()
        ).isEqualTo(
                "skyline-suite"
        );

        assertThat(
                availability.getFirst()
                        .maxGuests()
        ).isEqualTo(3);
    }

    @Test
    void availabilityRejectsCheckoutBeforeCheckin() {
        LocalDate checkIn =
                LocalDate.now()
                        .plusDays(30);

        LocalDate checkOut =
                checkIn.minusDays(1);

        assertThatThrownBy(
                () ->
                        tools.checkRoomAvailability(
                                "skyline-grand-hyderabad",
                                checkIn,
                                checkOut,
                                2
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "checkOut must be after checkIn."
                );
    }

    @Test
    void availabilityRejectsZeroGuests() {
        LocalDate checkIn =
                LocalDate.now()
                        .plusDays(30);

        LocalDate checkOut =
                checkIn.plusDays(3);

        assertThatThrownBy(
                () ->
                        tools.checkRoomAvailability(
                                "skyline-grand-hyderabad",
                                checkIn,
                                checkOut,
                                0
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "guests must be at least 1."
                );
    }

    @Test
    void staySearchReturnsOnlyHotelsWithSuitableInventory() {
        LocalDate checkIn =
                LocalDate.now()
                        .plusDays(30);

        LocalDate checkOut =
                checkIn.plusDays(2);

        List<HotelResponse> hotels =
                tools.searchHotels(
                        "Hyderabad",
                        null,
                        null,
                        null,
                        null,
                        null,
                        "recommended",
                        checkIn,
                        checkOut,
                        3
                );

        assertThat(hotels)
                .hasSize(1);

        assertThat(
                hotels.getFirst().slug()
        ).isEqualTo(
                "skyline-grand-hyderabad"
        );
    }
}
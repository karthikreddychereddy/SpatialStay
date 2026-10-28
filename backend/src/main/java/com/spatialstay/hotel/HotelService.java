package com.spatialstay.hotel;

import com.spatialstay.room.RoomRepository;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class HotelService {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final HotelMapper hotelMapper;

        public HotelService(
                HotelRepository hotelRepository,
                RoomRepository roomRepository,
                HotelMapper hotelMapper
        ) {
                this.hotelRepository = hotelRepository;
                this.roomRepository = roomRepository;
                this.hotelMapper = hotelMapper;
        }

    @Transactional(readOnly = true)
    public List<Hotel> searchHotels(
            HotelSearch search
    ) {
        List<Long> availableHotelIds =
                null;

        /*
         * Step 5:
         *
         * A stay search only exposes hotels
         * containing at least one ACTIVE room
         * that can accommodate the requested
         * guest count and has configured
         * inventory.
         *
         * At this stage availableUnits equals
         * totalUnits because bookings and
         * temporary inventory holds are not
         * persisted yet.
         *
         * Step 9 will extend this calculation
         * with overlapping bookings and active
         * holds for checkIn/checkOut.
         */
        if (search.hasStaySearch()) {
            availableHotelIds =
                    roomRepository
                            .findHotelIdsWithAvailableRoomForGuests(
                                    search.guests()
                            );

            if (availableHotelIds.isEmpty()) {
                return List.of();
            }
        }

        List<Long> finalAvailableHotelIds =
                availableHotelIds;

        Specification<Hotel> specification =
                (
                        root,
                        query,
                        builder
                ) -> {

                    List<Predicate> predicates =
                            new ArrayList<>();

                    // Public search only exposes active hotels.
                    predicates.add(
                            builder.equal(
                                    builder.upper(
                                            root.get(
                                                    "status"
                                            )
                                    ),
                                    "ACTIVE"
                            )
                    );

                    if (
                            finalAvailableHotelIds
                                    != null
                    ) {
                        predicates.add(
                                root.get("id")
                                        .in(
                                                finalAvailableHotelIds
                                        )
                        );
                    }

                    String destination =
                            search
                                    .normalizedDestination();

                    if (destination != null) {
                        String pattern =
                                "%" +
                                destination
                                        .toLowerCase() +
                                "%";

                        predicates.add(
                                builder.or(
                                        builder.like(
                                                builder.lower(
                                                        root.get(
                                                                "city"
                                                        )
                                                ),
                                                pattern
                                        ),
                                        builder.like(
                                                builder.lower(
                                                        root.get(
                                                                "state"
                                                        )
                                                ),
                                                pattern
                                        ),
                                        builder.like(
                                                builder.lower(
                                                        root.get(
                                                                "country"
                                                        )
                                                ),
                                                pattern
                                        )
                                )
                        );
                    }

                    if (
                            search.query() != null
                    ) {
                        String pattern =
                                "%" +
                                search.query()
                                        .toLowerCase() +
                                "%";

                        predicates.add(
                                builder.or(
                                        builder.like(
                                                builder.lower(
                                                        root.get(
                                                                "name"
                                                        )
                                                ),
                                                pattern
                                        ),
                                        builder.like(
                                                builder.lower(
                                                        root.get(
                                                                "description"
                                                        )
                                                ),
                                                pattern
                                        ),
                                        builder.like(
                                                builder.lower(
                                                        root.get(
                                                                "city"
                                                        )
                                                ),
                                                pattern
                                        ),
                                        builder.like(
                                                builder.lower(
                                                        root.get(
                                                                "state"
                                                        )
                                                ),
                                                pattern
                                        ),
                                        builder.like(
                                                builder.lower(
                                                        root.get(
                                                                "country"
                                                        )
                                                ),
                                                pattern
                                        )
                                )
                        );
                    }

                    if (
                            search.minPrice()
                                    != null
                    ) {
                        predicates.add(
                                builder.greaterThanOrEqualTo(
                                        root.get(
                                                "startingPrice"
                                        ),
                                        search.minPrice()
                                )
                        );
                    }

                    if (
                            search.maxPrice()
                                    != null
                    ) {
                        predicates.add(
                                builder.lessThanOrEqualTo(
                                        root.get(
                                                "startingPrice"
                                        ),
                                        search.maxPrice()
                                )
                        );
                    }

                    if (
                            search.minRating()
                                    != null
                    ) {
                        predicates.add(
                                builder.greaterThanOrEqualTo(
                                        root.get(
                                                "rating"
                                        ),
                                        search.minRating()
                                )
                        );
                    }

                    if (
                            search.verified()
                                    != null
                    ) {
                        predicates.add(
                                builder.equal(
                                        root.get(
                                                "verified"
                                        ),
                                        search.verified()
                                )
                        );
                    }

                    return builder.and(
                            predicates.toArray(
                                    Predicate[]::new
                            )
                    );
                };

        return hotelRepository.findAll(
                specification,
                search.resolvedSort()
        );
    }

    @Transactional(readOnly = true)
    public List<Hotel> getAllHotels() {
        return searchHotels(
                new HotelSearch(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "recommended",
                        null,
                        null,
                        null
                )
        );
    }

    @Transactional(readOnly = true)
    public Optional<Hotel>
    getHotelBySlug(
            String slug
    ) {
        return hotelRepository
                .findBySlug(slug)
                .filter(
                        hotel ->
                                "ACTIVE"
                                        .equalsIgnoreCase(
                                                hotel.getStatus()
                                        )
                );
    }

        @Transactional(readOnly = true)
        public List<HotelResponse> searchHotelResponses(
                HotelSearch search
        ) {
        return searchHotels(search)
                .stream()
                .map(hotelMapper::toResponse)
                .toList();
        }

        @Transactional(readOnly = true)
        public Optional<HotelResponse> getHotelResponseBySlug(
                String slug
        ) {
        return hotelRepository
                .findBySlug(slug)
                .filter(
                        hotel ->
                                "ACTIVE".equalsIgnoreCase(
                                        hotel.getStatus()
                                )
                )
                .map(hotelMapper::toResponse);
        }
}
package com.spatialstay.hotel;

import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

public record HotelSearch(
        String destination,
        String query,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal minRating,
        Boolean verified,
        String sort,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer guests
) {

    private static final Map<String, String>
            DESTINATION_ALIASES =
            Map.ofEntries(
                    Map.entry(
                            "vizag",
                            "Visakhapatnam"
                    ),
                    Map.entry(
                            "visakhapatnam",
                            "Visakhapatnam"
                    ),
                    Map.entry(
                            "hyd",
                            "Hyderabad"
                    ),
                    Map.entry(
                            "hyderabad",
                            "Hyderabad"
                    ),
                    Map.entry(
                            "goa",
                            "Goa"
                    )
            );

    public HotelSearch {
        destination = clean(destination);
        query = clean(query);
        sort = clean(sort);

        validatePrice(
                minPrice,
                "minPrice"
        );

        validatePrice(
                maxPrice,
                "maxPrice"
        );

        if (
                minPrice != null &&
                maxPrice != null &&
                minPrice.compareTo(
                        maxPrice
                ) > 0
        ) {
            throw new IllegalArgumentException(
                    "minPrice cannot be greater than maxPrice."
            );
        }

        if (
                minRating != null &&
                (
                        minRating.compareTo(
                                BigDecimal.ZERO
                        ) < 0 ||
                        minRating.compareTo(
                                BigDecimal.valueOf(5)
                        ) > 0
                )
        ) {
            throw new IllegalArgumentException(
                    "minRating must be between 0 and 5."
            );
        }

        validateSort(sort);

        validateStaySearch(
                checkIn,
                checkOut,
                guests
        );
    }

    public String normalizedDestination() {
        if (destination == null) {
            return null;
        }

        String normalized =
                destination.toLowerCase(
                        Locale.ROOT
                );

        return DESTINATION_ALIASES
                .getOrDefault(
                        normalized,
                        destination
                );
    }

    public boolean hasStaySearch() {
        return checkIn != null &&
                checkOut != null &&
                guests != null;
    }

    public Sort resolvedSort() {
        String value =
                sort == null
                        ? "recommended"
                        : sort.toLowerCase(
                                Locale.ROOT
                        );

        return switch (value) {
            case "price_asc" ->
                    Sort.by(
                            Sort.Order.asc(
                                    "startingPrice"
                            ),
                            Sort.Order.desc(
                                    "rating"
                            ).nullsLast()
                    );

            case "price_desc" ->
                    Sort.by(
                            Sort.Order.desc(
                                    "startingPrice"
                            ),
                            Sort.Order.desc(
                                    "rating"
                            ).nullsLast()
                    );

            case "rating_desc" ->
                    Sort.by(
                            Sort.Order.desc(
                                    "rating"
                            ).nullsLast(),
                            Sort.Order.desc(
                                    "reviewCount"
                            )
                    );

            case "reviews_desc" ->
                    Sort.by(
                            Sort.Order.desc(
                                    "reviewCount"
                            ),
                            Sort.Order.desc(
                                    "rating"
                            ).nullsLast()
                    );

            case "newest" ->
                    Sort.by(
                            Sort.Order.desc(
                                    "createdAt"
                            )
                    );

            default ->
                    Sort.by(
                            Sort.Order.desc(
                                    "verified"
                            ),
                            Sort.Order.desc(
                                    "rating"
                            ).nullsLast(),
                            Sort.Order.desc(
                                    "reviewCount"
                            )
                    );
        };
    }

    private static void validateStaySearch(
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests
    ) {
        boolean anyStayParameter =
                checkIn != null ||
                checkOut != null ||
                guests != null;

        if (!anyStayParameter) {
            return;
        }

        if (
                checkIn == null ||
                checkOut == null ||
                guests == null
        ) {
            throw new IllegalArgumentException(
                    "checkIn, checkOut and guests must be provided together."
            );
        }

        if (
                checkIn.isBefore(
                        LocalDate.now()
                )
        ) {
            throw new IllegalArgumentException(
                    "checkIn cannot be in the past."
            );
        }

        if (
                !checkOut.isAfter(
                        checkIn
                )
        ) {
            throw new IllegalArgumentException(
                    "checkOut must be after checkIn."
            );
        }

        if (guests < 1) {
            throw new IllegalArgumentException(
                    "guests must be at least 1."
            );
        }
    }

    private static void validatePrice(
            BigDecimal value,
            String field
    ) {
        if (
                value != null &&
                value.signum() < 0
        ) {
            throw new IllegalArgumentException(
                    field +
                            " cannot be negative."
            );
        }
    }

    private static void validateSort(
            String sort
    ) {
        if (sort == null) {
            return;
        }

        switch (
                sort.toLowerCase(
                        Locale.ROOT
                )
        ) {
            case "recommended",
                 "price_asc",
                 "price_desc",
                 "rating_desc",
                 "reviews_desc",
                 "newest" -> {
            }

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported sort value: " +
                                    sort
                    );
        }
    }

    private static String clean(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String cleaned =
                value.trim();

        return cleaned.isEmpty()
                ? null
                : cleaned;
    }
}
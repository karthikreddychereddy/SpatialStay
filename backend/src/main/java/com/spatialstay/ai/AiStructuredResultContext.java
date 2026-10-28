package com.spatialstay.ai;

import com.spatialstay.hotel.HotelResponse;
import com.spatialstay.room.RoomAvailabilityResponse;
import com.spatialstay.room.RoomResponse;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiStructuredResultContext {

    private final ThreadLocal<Result> current =
            ThreadLocal.withInitial(Result::empty);

    public void clear() {
        current.set(Result.empty());
    }

    public void captureHotels(List<HotelResponse> hotels) {
        current.set(
                new Result(
                        "HOTELS",
                        safeList(hotels),
                        List.of(),
                        List.of()
                )
        );
    }

    public void captureRooms(List<RoomResponse> rooms) {
        current.set(
                new Result(
                        "ROOMS",
                        List.of(),
                        safeList(rooms),
                        List.of()
                )
        );
    }

    public void captureAvailability(
            List<RoomAvailabilityResponse> availability
    ) {
        current.set(
                new Result(
                        "AVAILABILITY",
                        List.of(),
                        List.of(),
                        safeList(availability)
                )
        );
    }

    public Result snapshot() {
        return current.get();
    }

    public void remove() {
        current.remove();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null
                ? List.of()
                : List.copyOf(values);
    }

    public record Result(
            String responseType,
            List<HotelResponse> hotels,
            List<RoomResponse> rooms,
            List<RoomAvailabilityResponse> availability
    ) {

        public static Result empty() {
            return new Result(
                    "TEXT",
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }
}
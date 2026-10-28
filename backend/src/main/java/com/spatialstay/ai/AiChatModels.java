package com.spatialstay.ai;

import com.spatialstay.hotel.HotelResponse;
import com.spatialstay.room.RoomAvailabilityResponse;
import com.spatialstay.room.RoomResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AiChatModels {

    private AiChatModels() {
    }

    public record ChatRequest(
            @NotBlank(message = "message is required.")
            @Size(
                    max = 4000,
                    message = "message must not exceed 4000 characters."
            )
            String message
    ) {
    }

    public record ChatResponse(
            String message,
            String responseType,
            List<HotelResponse> hotels,
            List<RoomResponse> rooms,
            List<RoomAvailabilityResponse> availability
    ) {
    }

    public record SupervisorResponse(
            String message,
            String responseType,
            List<HotelResponse> hotels,
            List<RoomResponse> rooms,
            List<RoomAvailabilityResponse> availability
    ) {
    }

    public record ConversationResetResponse(
            String message
    ) {
    }

    public record VoiceRequest(
            @NotBlank(message = "sessionId is required.")
            @Size(
                    max = 200,
                    message = "sessionId must not exceed 200 characters."
            )
            String sessionId,

            @NotBlank(message = "message is required.")
            @Size(
                    max = 4000,
                    message = "message must not exceed 4000 characters."
            )
            String message
    ) {
    }

    public record VoiceResponse(
            String message,
            String responseType,
            List<HotelResponse> hotels,
            List<RoomResponse> rooms,
            List<RoomAvailabilityResponse> availability
    ) {
    }
}
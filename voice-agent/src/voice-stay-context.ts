import type {
  SpatialStayAvailabilityResult,
  SpatialStayHotelResult,
  SpatialStayRoomResult,
  SpatialStayVoiceResponse,
} from './spatialstay-client.ts';

export interface VoiceStayContextSnapshot {
  hotelId?: number;
  hotelSlug?: string;
  hotelName?: string;

  roomId?: number;
  roomSlug?: string;
  roomName?: string;

  checkIn?: string;
  checkOut?: string;
  guests?: number;

  nights?: number;
  pricePerNight?: number;
  estimatedStayTotal?: number;
  currency?: string;

  availabilityConfirmed?: boolean;
}

export class VoiceStayContext {
  private state: VoiceStayContextSnapshot = {};

  updateFromResponse(
    response: SpatialStayVoiceResponse,
  ): void {
    const hotel =
      this.selectHotel(response.hotels);

    const room =
      this.selectRoom(response.rooms);

    const availability =
      this.selectAvailability(
        response.availability,
      );

    if (hotel) {
      this.applyHotel(hotel);
    }

    if (room) {
      this.applyRoom(room);
    }

    if (availability) {
      this.applyAvailability(
        availability,
      );
    }

    this.logCurrentState();
  }

  snapshot(): VoiceStayContextSnapshot {
    return {
      ...this.state,
    };
  }

  clear(): void {
    this.state = {};

    console.log(
      '[SpatialStay] Voice stay context cleared',
    );
  }

  private selectHotel(
    hotels: SpatialStayHotelResult[],
  ): SpatialStayHotelResult | undefined {
    if (hotels.length === 1) {
      return hotels[0];
    }

    if (
      this.state.hotelId !== undefined
    ) {
      const currentHotel =
        hotels.find(
          (hotel) =>
            hotel.id ===
            this.state.hotelId,
        );

      if (currentHotel) {
        return currentHotel;
      }
    }

    return undefined;
  }

  private selectRoom(
    rooms: SpatialStayRoomResult[],
  ): SpatialStayRoomResult | undefined {
    if (rooms.length === 1) {
      return rooms[0];
    }

    if (
      this.state.roomId !== undefined
    ) {
      const currentRoom =
        rooms.find(
          (room) =>
            room.id ===
            this.state.roomId,
        );

      if (currentRoom) {
        return currentRoom;
      }
    }

    return undefined;
  }

  private selectAvailability(
    availability:
      SpatialStayAvailabilityResult[],
  ):
    | SpatialStayAvailabilityResult
    | undefined {
    const availableResults =
      availability.filter(
        (result) =>
          result.available,
      );

    if (
      availableResults.length === 1
    ) {
      return availableResults[0];
    }

    if (
      this.state.roomId !== undefined
    ) {
      const currentRoomResult =
        availability.find(
          (result) =>
            result.roomId ===
            this.state.roomId,
        );

      if (currentRoomResult) {
        return currentRoomResult;
      }
    }

    return undefined;
  }

  private applyHotel(
    hotel: SpatialStayHotelResult,
  ): void {
    const hotelChanged =
      this.state.hotelId !== undefined &&
      this.state.hotelId !== hotel.id;

    if (hotelChanged) {
      this.clearRoomAndStay();
    }

    this.state.hotelId =
      hotel.id;

    this.state.hotelSlug =
      hotel.slug;

    this.state.hotelName =
      hotel.name;
  }

  private applyRoom(
    room: SpatialStayRoomResult,
  ): void {
    const roomChanged =
      this.state.roomId !== undefined &&
      this.state.roomId !== room.id;

    if (roomChanged) {
      this.clearStayDetails();
    }

    this.state.roomId =
      room.id;

    this.state.roomSlug =
      room.slug;

    this.state.roomName =
      room.name;

    this.state.hotelSlug =
      room.hotelSlug;

    this.state.hotelName =
      room.hotelName;

    this.state.pricePerNight =
      room.pricePerNight;

    this.state.currency =
      room.currency;
  }

  private applyAvailability(
    result: SpatialStayAvailabilityResult,
  ): void {
    const roomChanged =
      this.state.roomId !== undefined &&
      this.state.roomId !==
        result.roomId;

    if (roomChanged) {
      this.clearStayDetails();
    }

    this.state.roomId =
      result.roomId;

    this.state.roomSlug =
      result.roomSlug;

    this.state.roomName =
      result.roomName;

    this.state.checkIn =
      result.checkIn;

    this.state.checkOut =
      result.checkOut;

    this.state.guests =
      result.guests;

    this.state.nights =
      result.nights;

    this.state.pricePerNight =
      result.pricePerNight;

    this.state.estimatedStayTotal =
      result.estimatedStayTotal;

    this.state.currency =
      result.currency;

    this.state.availabilityConfirmed =
      result.available;
  }

  private clearRoomAndStay(): void {
    delete this.state.roomId;
    delete this.state.roomSlug;
    delete this.state.roomName;

    this.clearStayDetails();
  }

  private clearStayDetails(): void {
    delete this.state.checkIn;
    delete this.state.checkOut;
    delete this.state.guests;
    delete this.state.nights;
    delete this.state.estimatedStayTotal;
    delete this.state.availabilityConfirmed;
  }

  private logCurrentState(): void {
    if (
      Object.keys(this.state).length === 0
    ) {
      return;
    }

    console.log(
      '[SpatialStay] Voice stay context:',
      this.snapshot(),
    );
  }
}
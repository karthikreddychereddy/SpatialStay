export interface SpatialStayHotelResult {
  id: number;
  slug: string;
  name: string;
  city: string;
  startingPrice: number;
  rating: number;
}

export interface SpatialStayRoomResult {
  id: number;
  slug: string;
  name: string;
  hotelName: string;
  hotelSlug: string;
  maxGuests: number;
  pricePerNight: number;
  currency: string;
}

export interface SpatialStayAvailabilityResult {
  roomId: number;
  roomName: string;
  roomSlug: string;
  checkIn: string;
  checkOut: string;
  guests: number;
  nights: number;
  pricePerNight: number;
  estimatedStayTotal: number;
  currency: string;
  available: boolean;
}

export interface SpatialStayVoiceResponse {
  message: string;
  responseType: string;
  hotels: SpatialStayHotelResult[];
  rooms: SpatialStayRoomResult[];
  availability: SpatialStayAvailabilityResult[];
}

interface SpatialStayErrorResponse {
  error?: string;
  message?: string;
}

const DEFAULT_BACKEND_URL = 'http://localhost:8080';
const REQUEST_TIMEOUT_MS = 90_000;

export class SpatialStayClient {
  private readonly backendUrl: string;
  private readonly internalKey: string;

  constructor() {
    this.backendUrl = (
      process.env.SPATIALSTAY_BACKEND_URL ??
      DEFAULT_BACKEND_URL
    ).replace(/\/+$/, '');

    this.internalKey =
      process.env.SPATIALSTAY_VOICE_INTERNAL_KEY?.trim() ?? '';

    if (!this.internalKey) {
      throw new Error(
        'SPATIALSTAY_VOICE_INTERNAL_KEY is required for the SpatialStay voice agent.',
      );
    }
  }

  async chat(
    sessionId: string,
    message: string,
    signal?: AbortSignal,
  ): Promise<SpatialStayVoiceResponse> {
    const cleanSessionId = sessionId.trim();
    const cleanMessage = message.trim();

    if (!cleanSessionId) {
      throw new Error('Voice session ID cannot be empty.');
    }

    if (!cleanMessage) {
      throw new Error('Voice message cannot be empty.');
    }

    const timeoutController = new AbortController();

    const timeout = setTimeout(() => {
      timeoutController.abort();
    }, REQUEST_TIMEOUT_MS);

    const abortFromCaller = () => {
      timeoutController.abort();
    };

    signal?.addEventListener(
      'abort',
      abortFromCaller,
      { once: true },
    );

    const requestStartedAt = performance.now();

    console.log(
      `[SpatialStay] Spring request started | session=${cleanSessionId}`,
    );

    try {
      const response = await fetch(
        `${this.backendUrl}/api/internal/ai/voice`,
        {
          method: 'POST',

          headers: {
            'Content-Type': 'application/json',
            Accept: 'application/json',
            'X-SpatialStay-Voice-Key':
              this.internalKey,
          },

          body: JSON.stringify({
            sessionId: cleanSessionId,
            message: cleanMessage,
          }),

          signal: timeoutController.signal,
        },
      );

      const requestDurationMs = Math.round(
        performance.now() - requestStartedAt,
      );

      console.log(
        `[SpatialStay] Spring response received | status=${response.status} | duration=${requestDurationMs}ms`,
      );

      if (!response.ok) {
        const errorBody =
          await this.readError(response);

        throw new Error(
          errorBody.message ??
            `SpatialStay backend returned HTTP ${response.status}.`,
        );
      }

      const body =
        (await response.json()) as SpatialStayVoiceResponse;

      if (
        !body ||
        typeof body.message !== 'string' ||
        !body.message.trim()
      ) {
        throw new Error(
          'SpatialStay backend returned an invalid voice response.',
        );
      }

      return {
        message: body.message.trim(),

        responseType:
          body.responseType ?? 'TEXT',

        hotels: Array.isArray(body.hotels)
          ? body.hotels
          : [],

        rooms: Array.isArray(body.rooms)
          ? body.rooms
          : [],

        availability: Array.isArray(
          body.availability,
        )
          ? body.availability
          : [],
      };
    } catch (error) {
      const requestDurationMs = Math.round(
        performance.now() - requestStartedAt,
      );

      if (
        error instanceof Error &&
        error.name === 'AbortError'
      ) {
        console.log(
          `[SpatialStay] Spring request aborted | duration=${requestDurationMs}ms`,
        );

        if (signal?.aborted) {
          throw error;
        }

        throw new Error(
          'SpatialStay backend request timed out.',
        );
      }

      throw error;
    } finally {
      clearTimeout(timeout);

      signal?.removeEventListener(
        'abort',
        abortFromCaller,
      );
    }
  }

  async clearSession(
    sessionId: string,
  ): Promise<void> {
    const cleanSessionId = sessionId.trim();

    if (!cleanSessionId) {
      return;
    }

    try {
      const response = await fetch(
        `${this.backendUrl}/api/internal/ai/voice/${encodeURIComponent(
          cleanSessionId,
        )}`,
        {
          method: 'DELETE',

          headers: {
            Accept: 'application/json',
            'X-SpatialStay-Voice-Key':
              this.internalKey,
          },
        },
      );

      if (!response.ok) {
        console.warn(
          `[SpatialStay] Voice context cleanup returned HTTP ${response.status} | session=${cleanSessionId}`,
        );

        return;
      }

      console.log(
        `[SpatialStay] Voice context cleared | session=${cleanSessionId}`,
      );
    } catch (error) {
      console.warn(
        `[SpatialStay] Voice context cleanup failed | session=${cleanSessionId}`,
        error,
      );
    }
  }

  private async readError(
    response: Response,
  ): Promise<SpatialStayErrorResponse> {
    try {
      return (await response.json()) as SpatialStayErrorResponse;
    } catch {
      return {
        message:
          `SpatialStay backend returned HTTP ${response.status}.`,
      };
    }
  }
}
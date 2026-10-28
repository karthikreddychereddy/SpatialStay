import {
  SipCallError,
  SipClient,
} from 'livekit-server-sdk';

const DEFAULT_RINGING_TIMEOUT_SECONDS = 30;
const DEFAULT_MAX_CALL_DURATION_SECONDS = 15 * 60;

export interface OutboundCallRequest {
  phoneNumber: string;

  /**
   * This must come from an authoritative application decision.
   * For future booking-confirmation calls, Spring will only set this
   * after consent has been verified.
   */
  consentVerified: boolean;

  purpose:
    | 'BOOKING_CONFIRMATION'
    | 'CUSTOMER_REQUEST'
    | 'SUPPORT';

  referenceId?: string;
}

export interface OutboundCallResult {
  roomName: string;
  participantIdentity: string;
  phoneNumber: string;

  status:
    | 'ANSWERED'
    | 'FAILED';

  sipCallId?: string;
}

function requiredEnvironmentVariable(
  name: string,
): string {
  const value =
    process.env[name]?.trim();

  if (!value) {
    throw new Error(
      `${name} is required for outbound calling.`,
    );
  }

  return value;
}

function normalizePhoneNumber(
  value: string,
): string {
  const cleanValue =
    value.trim().replace(/[\s()-]/g, '');

  /*
   * Require E.164-style international numbers.
   *
   * Examples:
   * +919876543210
   * +14155552671
   *
   * Do not silently infer a country code.
   */
  if (!/^\+[1-9]\d{7,14}$/.test(cleanValue)) {
    throw new Error(
      'Outbound phone number must use international E.164 format.',
    );
  }

  return cleanValue;
}

function createOutboundRoomName(): string {
  return (
    'spatialstay-outbound-' +
    crypto.randomUUID()
  );
}

function createParticipantIdentity(): string {
  return (
    'spatialstay-caller-' +
    crypto.randomUUID()
  );
}

export class SpatialStayOutboundCaller {
  private readonly sipClient: SipClient;

  private readonly outboundTrunkId: string;

  constructor() {
    const liveKitUrl =
      requiredEnvironmentVariable(
        'LIVEKIT_URL',
      );

    const apiKey =
      requiredEnvironmentVariable(
        'LIVEKIT_API_KEY',
      );

    const apiSecret =
      requiredEnvironmentVariable(
        'LIVEKIT_API_SECRET',
      );

    this.outboundTrunkId =
      requiredEnvironmentVariable(
        'SPATIALSTAY_SIP_OUTBOUND_TRUNK_ID',
      );

    this.sipClient =
      new SipClient(
        liveKitUrl,
        apiKey,
        apiSecret,
      );
  }

  async call(
    request: OutboundCallRequest,
  ): Promise<OutboundCallResult> {
    /*
     * The voice agent must never decide consent.
     *
     * Eventually Spring will make this decision from
     * persisted customer consent before invoking this
     * outbound infrastructure.
     */
    if (!request.consentVerified) {
      throw new Error(
        'Outbound call blocked because customer consent has not been verified.',
      );
    }

    const phoneNumber =
      normalizePhoneNumber(
        request.phoneNumber,
      );

    const roomName =
      createOutboundRoomName();

    const participantIdentity =
      createParticipantIdentity();

    const metadata = JSON.stringify({
      channel: 'sip-outbound',
      purpose: request.purpose,
      referenceId:
        request.referenceId ?? null,
    });

    console.log(
      '[SpatialStay] Outbound call requested',
      {
        roomName,
        participantIdentity,
        purpose: request.purpose,
        referenceId:
          request.referenceId ??
          'none',
      },
    );

    try {
      /*
       * LiveKit creates the SIP participant and
       * connects it to this isolated room.
       *
       * waitUntilAnswered=true means this method
       * resolves only after the destination answers.
       */
      const participant =
        await this.sipClient.createSipParticipant(
          this.outboundTrunkId,
          phoneNumber,
          roomName,
          {
            participantIdentity,

            participantName:
              'SpatialStay Customer',

            participantMetadata:
              metadata,

            participantAttributes: {
              'spatialstay.channel':
                'sip-outbound',

              'spatialstay.purpose':
                request.purpose,

              ...(request.referenceId
                ? {
                    'spatialstay.referenceId':
                      request.referenceId,
                  }
                : {}),
            },

            waitUntilAnswered: true,

            ringingTimeout:
              DEFAULT_RINGING_TIMEOUT_SECONDS,

            timeout:
              DEFAULT_RINGING_TIMEOUT_SECONDS +
              10,

            maxCallDuration:
              DEFAULT_MAX_CALL_DURATION_SECONDS,

            playDialtone: true,
          },
        );

      console.log(
        '[SpatialStay] Outbound call answered',
        {
          roomName,
          participantIdentity,
          sipCallId:
            participant.sipCallId,
          purpose:
            request.purpose,
        },
      );

      return {
        roomName,
        participantIdentity,
        phoneNumber,
        status: 'ANSWERED',
        sipCallId:
          participant.sipCallId,
      };
    } catch (error) {
      if (error instanceof SipCallError) {
        console.error(
          '[SpatialStay] Outbound SIP call failed',
          {
            roomName,
            participantIdentity,
            purpose:
              request.purpose,
            sipStatusCode:
              error.sipStatusCode,
            message:
              error.message,
          },
        );

        return {
          roomName,
          participantIdentity,
          phoneNumber,
          status: 'FAILED',
        };
      }

      console.error(
        '[SpatialStay] Outbound call failed',
        {
          roomName,
          participantIdentity,
          purpose:
            request.purpose,
          error,
        },
      );

      throw error;
    }
  }
}
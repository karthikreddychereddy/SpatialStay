import {
  ServerOptions,
  cli,
  defineAgent,
  inference,
  voice,
} from '@livekit/agents';

import {
  ParticipantKind,
} from '@livekit/rtc-node';

import dotenv from 'dotenv';
import { fileURLToPath } from 'node:url';

import {
  createAgent,
} from './agent.ts';

import {
  SpatialStayClient,
} from './spatialstay-client.ts';

import {
  VoiceStayContext,
} from './voice-stay-context.ts';

dotenv.config({
  path: '.env.local',
});

const WEB_GREETING =
  'Hi, I’m the SpatialStay voice assistant. How can I help with your stay?';

const PHONE_GREETING =
  'Hi, you’ve reached SpatialStay. I’m the SpatialStay AI voice assistant. How can I help with your stay?';

function safeValue(
  value: string | undefined,
): string {
  const cleanValue = value?.trim();

  return cleanValue || 'unknown';
}

function createSessionId(
  roomName: string | undefined,
): string {
  const cleanRoomName =
    roomName?.trim();

  if (cleanRoomName) {
    return cleanRoomName;
  }

  return `spatialstay-${crypto.randomUUID()}`;
}

export default defineAgent({
  entry: async (ctx) => {
    const sessionStartedAt =
      Date.now();

    const sessionId =
      createSessionId(
        ctx.room.name,
      );

    const client =
      new SpatialStayClient();

    const stayContext =
      new VoiceStayContext();

    let cleanupStarted =
      false;

    let isSipCall =
      false;

    let callerPhoneNumber =
      'unknown';

    let sipCallId =
      'unknown';

    let sipProviderCallId =
      'unknown';

    let sipTrunkId =
      'unknown';

    const cleanup =
      async (): Promise<void> => {
        if (cleanupStarted) {
          return;
        }

        cleanupStarted = true;

        const durationSeconds =
          Math.max(
            0,
            Math.round(
              (
                Date.now() -
                sessionStartedAt
              ) / 1000,
            ),
          );

        console.log(
          '[SpatialStay] Voice session cleanup started',
          {
            sessionId,
            channel:
              isSipCall
                ? 'sip'
                : 'webrtc',
            callerPhoneNumber,
            sipCallId,
            sipProviderCallId,
            sipTrunkId,
            durationSeconds,
          },
        );

        /*
         * Local voice state is only
         * convenience state.
         *
         * Spring remains authoritative.
         */
        stayContext.clear();

        /*
         * Remove the Spring-owned
         * conversation history for this
         * voice session.
         */
        await client.clearSession(
          sessionId,
        );

        console.log(
          '[SpatialStay] Voice session cleanup completed',
          {
            sessionId,
            durationSeconds,
          },
        );
      };

    ctx.addShutdownCallback(
      cleanup,
    );

    /*
     * Register this before connecting so
     * participants already present when
     * the agent connects are also handled.
     *
     * This gives us one common entry point
     * for browser/mobile WebRTC users and
     * future SIP callers.
     */
    ctx.addParticipantEntrypoint(
      async (_jobContext, participant) => {
        if (
          participant.kind ===
          ParticipantKind.SIP
        ) {
          isSipCall = true;

          callerPhoneNumber =
            safeValue(
              participant.attributes[
                'sip.phoneNumber'
              ],
            );

          sipCallId =
            safeValue(
              participant.attributes[
                'sip.callID'
              ],
            );

          sipProviderCallId =
            safeValue(
              participant.attributes[
                'sip.callIDFull'
              ],
            );

          sipTrunkId =
            safeValue(
              participant.attributes[
                'sip.trunkID'
              ],
            );

          console.log(
            '[SpatialStay] SIP caller connected',
            {
              sessionId,
              participantIdentity:
                participant.identity,
              callerPhoneNumber,
              sipCallId,
              sipProviderCallId,
              sipTrunkId,
              sipCallStatus:
                safeValue(
                  participant.attributes[
                    'sip.callStatus'
                  ],
                ),
              trunkPhoneNumber:
                safeValue(
                  participant.attributes[
                    'sip.trunkPhoneNumber'
                  ],
                ),
              dispatchRuleId:
                safeValue(
                  participant.attributes[
                    'sip.ruleID'
                  ],
                ),
            },
          );

          return;
        }

        console.log(
          '[SpatialStay] WebRTC participant connected',
          {
            sessionId,
            participantIdentity:
              participant.identity,
          },
        );
      },
    );

    const session =
      new voice.AgentSession({
        stt: new inference.STT({
          model:
            'assemblyai/universal-3-6-pro',

          language: 'en',
        }),

        tts: new inference.TTS({
          model:
            'fishaudio/s2.1-pro',

          voice:
            'fa4c9eb3dccc4806b382b40d61c6b10a',
        }),

        turnHandling: {
          turnDetection:
            new inference.TurnDetector(),

          interruption: {
            mode: 'adaptive',
          },

          /*
           * Spring must receive completed
           * user turns rather than
           * speculative partial speech.
           */
          preemptiveGeneration: {
            enabled: false,
          },
        },

        expressive: true,
      });

    await session.start({
      agent: createAgent(
        sessionId,
        client,
        stayContext,
      ),

      room: ctx.room,

      /*
       * LiveKit automatically closes the
       * session when the linked caller
       * disconnects normally.
       *
       * This applies to both WebRTC and
       * SIP participants.
       */
      inputOptions: {
        closeOnDisconnect: true,
      },
    });

    await ctx.connect();

    /*
     * The participant entrypoint registered
     * above runs for participants already in
     * the room as well as participants that
     * join after connect.
     *
     * Give that callback an event-loop turn
     * before selecting the greeting.
     */
    await new Promise<void>(
      (resolve) => {
        setTimeout(
          resolve,
          0,
        );
      },
    );

    const greeting =
      isSipCall
        ? PHONE_GREETING
        : WEB_GREETING;

    /*
     * Fixed greeting only.
     *
     * No Spring AI request is required,
     * and it must not become part of
     * Spring-owned conversation history.
     */
    await session.say(
      greeting,
      {
        allowInterruptions: true,
        addToChatCtx: false,
      },
    );

    console.log(
      '[SpatialStay] Voice session ready',
      {
        sessionId,
        channel:
          isSipCall
            ? 'sip'
            : 'webrtc',
      },
    );
  },
});

cli.runApp(
  new ServerOptions({
    agent:
      fileURLToPath(
        import.meta.url,
      ),

    agentName:
      'my-agent',
  }),
);
import {
  Agent,
  llm,
} from '@livekit/agents';

import {
  SpatialStayClient,
} from './spatialstay-client.ts';

import {
  VoiceStayContext,
} from './voice-stay-context.ts';

const FALLBACK_SESSION_ID =
  'spatialstay-voice';

function normalizeWhitespace(
  value: string,
): string {
  return value
    .replace(/\s+/g, ' ')
    .trim();
}

function removeExactRepeatedHalf(
  value: string,
): string {
  const cleanValue =
    normalizeWhitespace(value);

  if (!cleanValue) {
    return '';
  }

  const words =
    cleanValue.split(' ');

  if (
    words.length < 4 ||
    words.length % 2 !== 0
  ) {
    return cleanValue;
  }

  const middle =
    words.length / 2;

  const firstHalf =
    words
      .slice(0, middle)
      .join(' ');

  const secondHalf =
    words
      .slice(middle)
      .join(' ');

  if (
    firstHalf.toLocaleLowerCase() ===
    secondHalf.toLocaleLowerCase()
  ) {
    console.warn(
      '[SpatialStay] Collapsed exact duplicated final transcript.',
    );

    return firstHalf;
  }

  return cleanValue;
}

function latestUserMessage(
  chatCtx: llm.ChatContext,
): string {
  for (
    let index =
      chatCtx.items.length - 1;
    index >= 0;
    index -= 1
  ) {
    const item =
      chatCtx.items[index];

    if (!item) {
      continue;
    }

    if (item.type !== 'message') {
      continue;
    }

    if (item.role !== 'user') {
      continue;
    }

    const text =
      item.textContent?.trim();

    if (text) {
      return removeExactRepeatedHalf(
        text,
      );
    }
  }

  return '';
}

class SpatialStaySupervisorLLM
  extends llm.LLM {
  private readonly client:
    SpatialStayClient;

  private readonly sessionId: string;

  private readonly stayContext:
    VoiceStayContext;

  constructor(
    client: SpatialStayClient,
    sessionId: string,
    stayContext: VoiceStayContext,
  ) {
    super();

    this.client = client;

    this.sessionId =
      sessionId.trim() ||
      FALLBACK_SESSION_ID;

    this.stayContext =
      stayContext;
  }

  label(): string {
    return 'spatialstay-spring-supervisor';
  }

  override get model(): string {
    return 'spatialstay-ai-supervisor';
  }

  override get provider(): string {
    return 'spatialstay-spring';
  }

  chat({
    chatCtx,
    toolCtx,
    connOptions,
  }: Parameters<
    llm.LLM['chat']
  >[0]): llm.LLMStream {
    return new SpatialStaySupervisorStream(
      this,
      this.client,
      this.sessionId,
      this.stayContext,
      {
        chatCtx,

        ...(toolCtx !== undefined
          ? { toolCtx }
          : {}),

        ...(connOptions !== undefined
          ? { connOptions }
          : {}),
      },
    );
  }
}

class SpatialStaySupervisorStream
  extends llm.LLMStream {
  private readonly client:
    SpatialStayClient;

  private readonly sessionId: string;

  private readonly stayContext:
    VoiceStayContext;

  constructor(
    parent: SpatialStaySupervisorLLM,
    client: SpatialStayClient,
    sessionId: string,
    stayContext: VoiceStayContext,
    options: {
      chatCtx: llm.ChatContext;

      toolCtx?: llm.ToolContextLike;

      connOptions?: Parameters<
        llm.LLM['chat']
      >[0]['connOptions'];
    },
  ) {
    super(parent, {
      chatCtx:
        options.chatCtx,

      ...(options.toolCtx !== undefined
        ? {
            toolCtx:
              options.toolCtx,
          }
        : {}),

      connOptions:
        options.connOptions ?? {
          maxRetry: 0,
          retryIntervalMs: 0,
          timeoutMs: 90_000,
        },
    });

    this.client =
      client;

    this.sessionId =
      sessionId;

    this.stayContext =
      stayContext;
  }

  protected override get genAIOperationName():
    | string
    | undefined {
    return undefined;
  }

  protected async run():
    Promise<void> {
    const userMessage =
      latestUserMessage(
        this.chatCtx,
      );

    if (!userMessage) {
      return;
    }

    try {
      const response =
        await this.client.chat(
          this.sessionId,
          userMessage,
          this.abortController.signal,
        );

      if (
        this.abortController.signal
          .aborted
      ) {
        return;
      }

      /*
       * Only authoritative Spring results
       * are allowed to update voice state.
       */
      this.stayContext
        .updateFromResponse(
          response,
        );

      this.output.put({
        id: crypto.randomUUID(),

        delta: {
          role: 'assistant',

          content:
            response.message,
        },
      });
    } catch (error) {
      if (
        this.abortController.signal
          .aborted
      ) {
        console.log(
          `[SpatialStay] Supervisor turn cancelled | session=${this.sessionId}`,
        );

        return;
      }

      this.logger.error(
        {
          err: error,

          sessionId:
            this.sessionId,
        },
        'SpatialStay Supervisor request failed',
      );

      this.output.put({
        id: crypto.randomUUID(),

        delta: {
          role: 'assistant',

          content:
            'I’m having trouble reaching the SpatialStay service right now. Please try again in a moment.',
        },
      });
    }
  }
}

export function createAgent(
  sessionId: string,
  client: SpatialStayClient,
  stayContext: VoiceStayContext,
) {
  return Agent.create({
    instructions:
      'You are the SpatialStay voice interface. ' +
      'The Spring SpatialStay AI Supervisor is the authoritative intelligence. ' +
      'Every genuine user request is delegated to that Supervisor. ' +
      'Do not invent hotel, room, availability, booking, payment, cancellation, modification, or pricing information.',

    llm:
      new SpatialStaySupervisorLLM(
        client,
        sessionId,
        stayContext,
      ),
  });
}
"use client";
import { AnimatePresence, motion } from "framer-motion";
import {
  AlertCircle,
  BedDouble,
  CalendarDays,
  Check,
  ChevronRight,
  Hotel,
  Keyboard,
  LoaderCircle,
  MapPin,
  Mic,
  MicOff,
  PhoneOff,
  RotateCcw,
  Sparkles,
  Users,
  Volume2,
  VolumeX,
} from "lucide-react";
import Link from "next/link";
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  AiApiError,
  AiChatResponse,
  aiApi,
} from "@/lib/api/ai";
type VoiceState =
  | "ready"
  | "requesting"
  | "listening"
  | "thinking"
  | "speaking"
  | "error"
  | "unsupported";
type SpeechRecognitionEventLike = {
  results: ArrayLike<{
    0: {
      transcript: string;
      confidence?: number;
    };
    isFinal: boolean;
  }>;
};
type SpeechRecognitionErrorEventLike = {
  error: string;
};
type SpeechRecognitionLike = {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onstart: (() => void) | null;
  onresult:
    | ((event: SpeechRecognitionEventLike) => void)
    | null;
  onerror:
    | ((event: SpeechRecognitionErrorEventLike) => void)
    | null;
  onend: (() => void) | null;
};
type SpeechRecognitionConstructor =
  new () => SpeechRecognitionLike;
declare global {
  interface Window {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  }
}
const VOICE_REOPEN_DELAY_MS = 6000;
type VoiceTurn = {
  id: string;
  role: "user" | "assistant";
  text: string;
};
type StayReadiness = "incomplete" | "ready-to-confirm";
function normalizeTranscript(value: string) {
  return value.replace(/\s+/g, " ").trim();
}
type StayContext = {
  hotelName?: string;
  hotelSlug?: string;
  roomName?: string;
  roomSlug?: string;
  checkIn?: string;
  checkOut?: string;
  guests?: number;
  nights?: number;
  pricePerNight?: number;
  estimatedTotal?: number;
  currency?: string;
  configuredAvailable?: boolean;
};
function createTurnId() {
  return `${Date.now()}-${Math.random()
    .toString(36)
    .slice(2)}`;
}
function formatMoney(
  amount?: number,
  currency = "INR",
) {
  if (amount === undefined || amount === null) {
    return null;
  }
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 0,
    }).format(amount);
  } catch {
    return `${currency} ${amount}`;
  }
}
function formatDate(value?: string) {
  if (!value) {
    return null;
  }
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  }).format(date);
}
export default function VoiceAIPage() {
  const [state, setState] =
    useState<VoiceState>("ready");
  const [supported, setSupported] =
    useState(true);
  const [
    voiceSessionActive,
    setVoiceSessionActive,
  ] = useState(false);
  const [
    speakerEnabled,
    setSpeakerEnabled,
  ] = useState(true);
  const [transcript, setTranscript] =
    useState("");
  const [
    interimTranscript,
    setInterimTranscript,
  ] = useState("");
  const [
    lastResponse,
    setLastResponse,
  ] =
    useState<AiChatResponse | null>(
      null,
    );
  const [turns, setTurns] =
    useState<VoiceTurn[]>([]);
  const [
    errorMessage,
    setErrorMessage,
  ] = useState("");
  const [
    stayContext,
    setStayContext,
  ] = useState<StayContext>({});
  const [recognitionConfidence, setRecognitionConfidence] = useState<number | null>(null);
  const lastSubmittedTranscriptRef = useRef("");
  const lastSubmittedAtRef = useRef(0);
  const recognitionRef =
    useRef<SpeechRecognitionLike | null>(
      null,
    );
  const finalTranscriptRef =
    useRef("");
  const requestInFlightRef =
    useRef(false);
  const intentionallyStoppedRef =
    useRef(false);
  const mountedRef =
    useRef(true);
  const stateRef =
    useRef<VoiceState>("ready");
  const voiceSessionActiveRef =
    useRef(false);
  const beginListeningRef =
    useRef<() => void>(() => {});
  const resumeTimerRef =
    useRef<ReturnType<
      typeof setTimeout
    > | null>(null);
  useEffect(() => {
    stateRef.current = state;
  }, [state]);
  useEffect(() => {
    voiceSessionActiveRef.current =
      voiceSessionActive;
  }, [voiceSessionActive]);
  const clearResumeTimer =
    useCallback(() => {
      if (resumeTimerRef.current) {
        clearTimeout(
          resumeTimerRef.current,
        );
        resumeTimerRef.current =
          null;
      }
    }, []);
  const resumeListeningIfActive =
    useCallback(
      (delay = 250) => {
        clearResumeTimer();
        if (
          !voiceSessionActiveRef.current ||
          !mountedRef.current
        ) {
          return;
        }
        resumeTimerRef.current =
          setTimeout(() => {
            resumeTimerRef.current =
              null;
            if (
              mountedRef.current &&
              voiceSessionActiveRef.current &&
              !requestInFlightRef.current &&
              !recognitionRef.current
            ) {
              beginListeningRef.current();
            }
          }, delay);
      },
      [clearResumeTimer],
    );
  const stopSpeech =
    useCallback(() => {
      if (
        typeof window !== "undefined" &&
        "speechSynthesis" in window
      ) {
        window.speechSynthesis.cancel();
      }
    }, []);
  const stopRecognition =
    useCallback(() => {
      const recognition =
        recognitionRef.current;
      if (!recognition) {
        return;
      }
      intentionallyStoppedRef.current =
        true;
      try {
        recognition.stop();
      } catch {
        try {
          recognition.abort();
        } catch {
          // Already stopped.
        }
      }
    }, []);
  useEffect(() => {
    mountedRef.current = true;
    if (
      typeof window === "undefined"
    ) {
      return;
    }
    const Recognition =
      window.SpeechRecognition ??
      window.webkitSpeechRecognition;
    if (!Recognition) {
      setSupported(false);
      setState("unsupported");
    }
    return () => {
      mountedRef.current = false;
      voiceSessionActiveRef.current =
        false;
      clearResumeTimer();
      stopSpeech();
      try {
        recognitionRef.current?.abort();
      } catch {
        // Already stopped.
      }
      recognitionRef.current = null;
    };
  }, [
    clearResumeTimer,
    stopSpeech,
  ]);
  const updateStayContext =
    useCallback(
      (response: AiChatResponse) => {
        setStayContext((current) => {
          const next = {
            ...current,
          };
          if (
            response.hotels &&
            response.hotels.length === 1
          ) {
            const hotel =
              response.hotels[0];
            next.hotelName =
              hotel.name;
            next.hotelSlug =
              hotel.slug;
          }
          if (
            response.rooms &&
            response.rooms.length === 1
          ) {
            const room =
              response.rooms[0];
            next.hotelName =
              room.hotelName ??
              next.hotelName;
            next.hotelSlug =
              room.hotelSlug ??
              next.hotelSlug;
            next.roomName =
              room.name;
            next.roomSlug =
              room.slug;
            next.pricePerNight =
              room.pricePerNight;
            next.currency =
              room.currency;
          }
          if (
            response.availability &&
            response.availability
              .length === 1
          ) {
            const availability =
              response.availability[0];
            next.roomName =
              availability.roomName;
            next.roomSlug =
              availability.roomSlug;
            next.checkIn =
              availability.checkIn;
            next.checkOut =
              availability.checkOut;
            next.guests =
              availability.guests;
            next.nights =
              availability.nights;
            next.pricePerNight =
              availability.pricePerNight;
            next.estimatedTotal =
              availability.estimatedStayTotal;
            next.currency =
              availability.currency;
            next.configuredAvailable =
              availability.available;
          }
          return next;
        });
      },
      [],
    );
  const speak = useCallback(
    (text: string) => {
      if (
        !speakerEnabled ||
        typeof window === "undefined" ||
        !("speechSynthesis" in window) ||
        !text.trim()
      ) {
        setState("ready");
        resumeListeningIfActive(
          VOICE_REOPEN_DELAY_MS,
        );
        return;
      }
      stopSpeech();
      const utterance =
        new SpeechSynthesisUtterance(
          text.trim(),
        );
      utterance.lang = "en-IN";
      utterance.rate = 1;
      utterance.pitch = 1;
      utterance.onstart = () => {
        if (mountedRef.current) {
          setState("speaking");
        }
      };
      utterance.onend = () => {
        if (!mountedRef.current) {
          return;
        }
        setState("ready");
        /*
         * Give the user time to read the
         * answer before listening starts
         * again.
         */
        resumeListeningIfActive(
          VOICE_REOPEN_DELAY_MS,
        );
      };
      utterance.onerror = () => {
        if (!mountedRef.current) {
          return;
        }
        setState("ready");
        resumeListeningIfActive(
          VOICE_REOPEN_DELAY_MS,
        );
      };
      window.speechSynthesis.speak(
        utterance,
      );
    },
    [
      speakerEnabled,
      stopSpeech,
      resumeListeningIfActive,
    ],
  );
  const sendTranscript =
    useCallback(
      async (spokenText: string) => {
        const cleanText = normalizeTranscript(spokenText);
        if (!cleanText || requestInFlightRef.current) {
          return;
        }
        const now = Date.now();
        const duplicateTurn =
          cleanText.toLowerCase() === lastSubmittedTranscriptRef.current.toLowerCase() &&
          now - lastSubmittedAtRef.current < 2500;
        if (duplicateTurn) {
          return;
        }
        lastSubmittedTranscriptRef.current = cleanText;
        lastSubmittedAtRef.current = now;
        requestInFlightRef.current = true;
        stopSpeech();
        setTranscript(cleanText);
        setInterimTranscript("");
        setErrorMessage("");
        setTurns((current) => [
          ...current,
          {
            id: createTurnId(),
            role: "user",
            text: cleanText,
          },
        ]);
        setState("thinking");
        try {
          const response =
            await aiApi.chat(
              cleanText,
            );
          if (!mountedRef.current) {
            return;
          }
          setLastResponse(response);
          updateStayContext(
            response,
          );
          setTurns((current) => [
            ...current,
            {
              id: createTurnId(),
              role: "assistant",
              text: response.message,
            },
          ]);
          speak(response.message);
        } catch (error) {
          if (!mountedRef.current) {
            return;
          }
          let message =
            "Spatial AI is temporarily unavailable. Please try again.";
          if (
            error instanceof
            AiApiError
          ) {
            if (
              error.status === 401 ||
              error.status === 403
            ) {
              message =
                "Your SpatialStay session is no longer active. Please sign in again.";
            } else if (
              error.status === 429 ||
              error.status === 503
            ) {
              message =
                "Spatial AI is temporarily busy. Please try again shortly.";
            } else if (
              error.message
            ) {
              message =
                error.message;
            }
          }
          setErrorMessage(message);
          setState("error");
        } finally {
          requestInFlightRef.current =
            false;
        }
      },
      [
        speak,
        stopSpeech,
        updateStayContext,
      ],
    );
  const beginListening =
    useCallback(() => {
      if (!supported) {
        setState("unsupported");
        return;
      }
      if (
        requestInFlightRef.current
      ) {
        return;
      }
      clearResumeTimer();
      stopSpeech();
      const Recognition =
        window.SpeechRecognition ??
        window.webkitSpeechRecognition;
      if (!Recognition) {
        setSupported(false);
        setState("unsupported");
        return;
      }
      setErrorMessage("");
      setTranscript("");
      setInterimTranscript("");
      setRecognitionConfidence(null);
      finalTranscriptRef.current = "";
      intentionallyStoppedRef.current =
        false;
      setState("requesting");
      const recognition =
        new Recognition();
      /*
       * One utterance per recognizer.
       * The VOICE SESSION remains active.
       */
      recognition.continuous = false;
      recognition.interimResults =
        true;
      recognition.lang = "en-IN";
      recognition.onstart = () => {
        if (mountedRef.current) {
          setState("listening");
        }
      };
      recognition.onresult = (
        event,
      ) => {
        let finalText =
          finalTranscriptRef.current;
        let interimText = "";
        for (
          let index = 0;
          index <
          event.results.length;
          index += 1
        ) {
          const result =
            event.results[index];
          const resultText = result?.[0]?.transcript ?? "";
          const confidence = result?.[0]?.confidence;
          if (result.isFinal && typeof confidence === "number" && confidence > 0) {
            setRecognitionConfidence(confidence);
          }
          if (result.isFinal) {
            finalText +=
              `${resultText} `;
          } else {
            interimText +=
              `${resultText} `;
          }
        }
        finalTranscriptRef.current = normalizeTranscript(finalText);
        setTranscript(
          finalTranscriptRef.current,
        );
        setInterimTranscript(normalizeTranscript(interimText));
      };
      recognition.onerror = (
        event,
      ) => {
        if (!mountedRef.current) {
          return;
        }
        if (
          event.error === "aborted" &&
          intentionallyStoppedRef.current
        ) {
          return;
        }
        if (
          event.error ===
            "not-allowed" ||
          event.error ===
            "service-not-allowed"
        ) {
          voiceSessionActiveRef.current =
            false;
          setVoiceSessionActive(
            false,
          );
          setErrorMessage(
            "Microphone permission was denied. Allow microphone access and try again.",
          );
          setState("error");
        } else if (
          event.error === "no-speech"
        ) {
          setErrorMessage("");
          setState("ready");
        } else {
          voiceSessionActiveRef.current =
            false;
          setVoiceSessionActive(
            false,
          );
          setErrorMessage(
            "Speech recognition stopped unexpectedly. Please try again.",
          );
          setState("error");
        }
      };
      recognition.onend = () => {
        recognitionRef.current =
          null;
        if (!mountedRef.current) {
          return;
        }
        const spokenText = normalizeTranscript(finalTranscriptRef.current);
        /*
         * Once we captured speech, the
         * listening orb disappears because
         * state becomes THINKING.
         */
        if (spokenText) {
          void sendTranscript(
            spokenText,
          );
          return;
        }
        if (
          intentionallyStoppedRef.current
        ) {
          setState("ready");
          return;
        }
        if (
          stateRef.current !== "error"
        ) {
          setState("ready");
          /*
           * Silence recovery can be faster
           * because no AI response occurred.
           */
          if (
            voiceSessionActiveRef.current
          ) {
            resumeListeningIfActive(
              700,
            );
          }
        }
      };
      recognitionRef.current =
        recognition;
      try {
        recognition.start();
      } catch {
        recognitionRef.current =
          null;
        setErrorMessage(
          "The microphone could not be started. Please try again.",
        );
        setState("error");
      }
    },
    [
      clearResumeTimer,
      resumeListeningIfActive,
      sendTranscript,
      stopSpeech,
      supported,
    ],
  );
  useEffect(() => {
    beginListeningRef.current =
      beginListening;
  }, [beginListening]);
  function stopVoiceSession() {
    voiceSessionActiveRef.current =
      false;
    setVoiceSessionActive(false);
    clearResumeTimer();
    stopRecognition();
    stopSpeech();
    setInterimTranscript("");
    if (
      stateRef.current !== "error" &&
      stateRef.current !==
        "unsupported"
    ) {
      setState("ready");
    }
  }
  function handleOrbClick() {
    if (
      voiceSessionActiveRef.current
    ) {
      stopVoiceSession();
      return;
    }
    if (!supported) {
      setState("unsupported");
      return;
    }
    voiceSessionActiveRef.current =
      true;
    setVoiceSessionActive(true);
    intentionallyStoppedRef.current =
      false;
    setErrorMessage("");
    beginListening();
  }
  function toggleSpeaker() {
    setSpeakerEnabled(
      (current) => {
        const next = !current;
        if (!next) {
          stopSpeech();
          if (
            stateRef.current ===
            "speaking"
          ) {
            setState("ready");
            resumeListeningIfActive(
              VOICE_REOPEN_DELAY_MS,
            );
          }
        }
        return next;
      },
    );
  }
  async function resetConversation() {
    voiceSessionActiveRef.current =
      false;
    setVoiceSessionActive(false);
    clearResumeTimer();
    stopRecognition();
    stopSpeech();
    setTranscript("");
    setInterimTranscript("");
    setLastResponse(null);
    setTurns([]);
    setStayContext({});
    setErrorMessage("");
    setRecognitionConfidence(null);
    lastSubmittedTranscriptRef.current = "";
    lastSubmittedAtRef.current = 0;
    finalTranscriptRef.current =
      "";
    try {
      await aiApi.resetConversation();
      setState("ready");
    } catch {
      setErrorMessage(
        "The conversation could not be reset. Please try again.",
      );
      setState("error");
    }
  }
  function endVoiceSession() {
    voiceSessionActiveRef.current =
      false;
    setVoiceSessionActive(false);
    clearResumeTimer();
    intentionallyStoppedRef.current =
      true;
    try {
      recognitionRef.current?.abort();
    } catch {
      // Already stopped.
    }
    recognitionRef.current = null;
    stopSpeech();
    finalTranscriptRef.current =
      "";
    requestInFlightRef.current =
      false;
    setTranscript("");
    setInterimTranscript("");
    setRecognitionConfidence(null);
    setState("ready");
  }
  const voiceActive =
    voiceSessionActive;
  const microphoneActive =
    state === "requesting" ||
    state === "listening";
  const showListeningOverlay =
    voiceActive &&
    microphoneActive;
  const visibleTranscript = [
    transcript,
    interimTranscript,
  ]
    .filter(Boolean)
    .join(" ")
    .trim();
  const hasStayContext =
    useMemo(
      () =>
        Boolean(
          stayContext.hotelName ||
            stayContext.roomName ||
            stayContext.checkIn ||
            stayContext.checkOut ||
            stayContext.guests,
        ),
      [stayContext],
    );
  const stayReadiness = useMemo<StayReadiness>(() => {
    const complete = Boolean(
      stayContext.hotelName &&
        stayContext.roomName &&
        stayContext.checkIn &&
        stayContext.checkOut &&
        stayContext.guests &&
        stayContext.guests > 0,
    );
    return complete ? "ready-to-confirm" : "incomplete";
  }, [stayContext]);
  return (
    <main
      className="
        min-h-screen
        bg-[radial-gradient(circle_at_50%_10%,#315f9d_0%,#102d59_38%,#071a36_72%,#041126_100%)]
        px-3 pb-3 pt-24
        text-white
        sm:px-5
        lg:h-screen
        lg:overflow-hidden
        lg:pt-28
      "
    >
      <section
        className="
          relative mx-auto
          flex
          min-h-[calc(100vh-112px)]
          max-w-[1480px]
          flex-col
          overflow-hidden
          rounded-[30px]
          border border-white/15
          bg-white/[.055]
          shadow-[0_35px_100px_rgba(0,0,0,.35)]
          backdrop-blur-2xl
          lg:h-[calc(100vh-124px)]
          lg:min-h-0
        "
      >
        <div
          className="
            pointer-events-none
            absolute inset-0
            bg-[radial-gradient(circle_at_35%_25%,rgba(87,139,255,.14),transparent_40%)]
          "
        />
        <header
          className="
            relative z-40
            flex shrink-0
            items-center justify-between
            border-b border-white/10
            bg-black/[.07]
            px-4 py-3
            backdrop-blur-xl
            sm:px-5
          "
        >
          <div className="flex min-w-0 items-center gap-3">
            <span
              className="
                grid h-9 w-9
                shrink-0
                place-items-center
                rounded-xl
                border border-white/10
                bg-white/10
              "
            >
              <Sparkles
                size={15}
                className="text-[#b8d0ff]"
              />
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-extrabold">
                Spatial AI Voice
              </p>
              <p className="hidden text-[10px] text-white/40 sm:block">
                Hands-free conversation
                with the same Spatial AI
                Supervisor
              </p>
            </div>
          </div>
          <Link
            href="/ai-concierge"
            onClick={endVoiceSession}
            className="
              flex shrink-0
              items-center gap-2
              rounded-full
              border border-white/15
              bg-white/10
              px-3.5 py-2
              text-[10px]
              font-extrabold
              transition
              hover:bg-white/15
              sm:px-4
              sm:text-xs
            "
          >
            <Keyboard size={14} />
            <span className="hidden sm:inline">
              Switch to chat
            </span>
            <span className="sm:hidden">
              Chat
            </span>
          </Link>
        </header>
        <section
          className="
            relative z-10
            min-h-0 flex-1
            overflow-hidden
          "
        >
          <div className="flex h-full min-h-0 flex-col">
            <div
              className="
                flex shrink-0
                items-center justify-between
                gap-3
                border-b border-white/10
                bg-black/[.025]
                px-4 py-3
                sm:px-6
              "
            >
              <div>
                <p className="text-sm font-extrabold">
                  Live conversation
                </p>
                <p className="mt-0.5 text-[10px] text-white/40 sm:text-xs">
                  Your conversation,
                  hotel results and stay
                  context remain visible
                  here.
                </p>
              </div>
              <div className="flex items-center gap-2">
                {voiceActive && (
                  <span
                    className="
                      hidden items-center
                      gap-1.5
                      rounded-full
                      border
                      border-[#61e1b0]/20
                      bg-[#61e1b0]/10
                      px-2.5 py-1
                      text-[9px]
                      font-extrabold
                      text-[#8df0c8]
                      sm:flex
                    "
                  >
                    <span
                      className={`
                        h-1.5 w-1.5
                        rounded-full
                        ${
                          microphoneActive
                            ? "animate-pulse bg-[#61e1b0]"
                            : state ===
                                "speaking"
                              ? "bg-[#a78bfa]"
                              : "bg-[#8db4ff]"
                        }
                      `}
                    />
                    {microphoneActive
                      ? "Listening"
                      : state ===
                          "thinking"
                        ? "Understanding"
                        : state ===
                            "speaking"
                          ? "Speaking"
                          : "Voice active"}
                  </span>
                )}
                {hasStayContext && (
                  <span
                    className="
                      hidden rounded-full
                      border border-white/10
                      bg-white/[.06]
                      px-3 py-1
                      text-[9px]
                      font-bold
                      text-white/55
                      md:inline
                    "
                  >
                    Stay context active
                  </span>
                )}
              </div>
            </div>
            <div
              className="
                min-h-0 flex-1
                overflow-y-auto
                overscroll-contain
                px-4 py-5
                [scrollbar-width:none]
                [-ms-overflow-style:none]
                [&::-webkit-scrollbar]:hidden
                sm:px-6
                lg:px-8
              "
            >
              <div
                className="
                  mx-auto w-full
                  max-w-6xl
                  space-y-5
                  pb-28
                "
              >
                {turns.length === 0 ? (
                  <div
                    className="
                      mx-auto mt-10
                      max-w-2xl
                      rounded-[28px]
                      border border-white/10
                      bg-white/[.04]
                      p-7 text-center
                      sm:mt-16
                    "
                  >
                    <span
                      className="
                        mx-auto grid
                        h-12 w-12
                        place-items-center
                        rounded-2xl
                        border border-white/10
                        bg-[#6a86f5]/15
                      "
                    >
                      <Sparkles
                        size={20}
                        className="text-[#b8d0ff]"
                      />
                    </span>
                    <h1 className="mt-4 text-xl font-extrabold sm:text-2xl">
                      Where would you
                      like to stay?
                    </h1>
                    <p
                      className="
                        mx-auto mt-2
                        max-w-xl
                        text-xs
                        leading-6
                        text-white/45
                        sm:text-sm
                      "
                    >
                      Tap the floating
                      microphone and speak
                      naturally. Spatial AI
                      keeps the conversation
                      context between turns.
                    </p>
                  </div>
                ) : (
                  turns.map((turn) => (
                    <div
                      key={turn.id}
                      className={`
                        flex
                        ${
                          turn.role ===
                          "user"
                            ? "justify-end"
                            : "justify-start"
                        }
                      `}
                    >
                      <div
                        className={`
                          max-w-[90%]
                          rounded-[22px]
                          px-4 py-3
                          sm:max-w-[78%]
                          lg:max-w-[72%]
                          ${
                            turn.role ===
                            "user"
                              ? "rounded-br-md bg-white/10"
                              : "rounded-bl-md border border-white/10 bg-[#5c7cf2]/10"
                          }
                        `}
                      >
                        <p
                          className="
                            mb-1 text-[9px]
                            font-extrabold
                            uppercase
                            tracking-[.15em]
                            text-white/35
                          "
                        >
                          {turn.role ===
                          "user"
                            ? "You"
                            : "Spatial AI"}
                        </p>
                        <p className="text-sm leading-6 text-white/80">
                          {turn.text}
                        </p>
                      </div>
                    </div>
                  ))
                )}
                {state ===
                  "thinking" && (
                  <div className="flex justify-start">
                    <div
                      className="
                        flex items-center
                        gap-2
                        rounded-2xl
                        border border-white/10
                        bg-white/[.04]
                        px-4 py-3
                        text-xs
                        text-white/45
                      "
                    >
                      <LoaderCircle
                        size={14}
                        className="animate-spin"
                      />
                      Understanding and
                      checking SpatialStay…
                    </div>
                  </div>
                )}
                {lastResponse && (
                  <VoiceResults
                    response={
                      lastResponse
                    }
                  />
                )}
                {hasStayContext && (
                  <div
                    className="
                      rounded-[22px]
                      border border-white/10
                      bg-black/10
                      p-4
                    "
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <p
                          className="
                            text-[10px]
                            font-extrabold
                            uppercase
                            tracking-[.16em]
                            text-[#a9c5ff]
                          "
                        >
                          Current stay
                        </p>
                        <p className="mt-1 text-[10px] text-white/35">
                          Conversation context only — nothing is reserved or held yet.
                        </p>
                        <div className="mt-2 flex flex-wrap items-center gap-2">
                          <span
                            className={`rounded-full border px-2.5 py-1 text-[9px] font-extrabold ${
                              stayReadiness === "ready-to-confirm"
                                ? "border-[#61e1b0]/20 bg-[#61e1b0]/10 text-[#8df0c8]"
                                : "border-white/10 bg-white/[.05] text-white/45"
                            }`}
                          >
                            {stayReadiness === "ready-to-confirm"
                              ? "Ready to confirm details"
                              : "More stay details needed"}
                          </span>
                          {recognitionConfidence !== null && (
                            <span className="text-[9px] text-white/30">
                              Speech confidence {Math.round(recognitionConfidence * 100)}%
                            </span>
                          )}
                        </div>
                      </div>
                      <Sparkles
                        size={15}
                        className="text-[#9ebdff]"
                      />
                    </div>
                    <div
                      className="
                        mt-3 grid gap-2
                        sm:grid-cols-2
                        lg:grid-cols-3
                      "
                    >
                      {stayContext.hotelName && (
                        <ContextItem
                          icon={
                            <Hotel
                              size={14}
                            />
                          }
                          label="Hotel"
                          value={
                            stayContext.hotelName
                          }
                        />
                      )}
                      {stayContext.roomName && (
                        <ContextItem
                          icon={
                            <BedDouble
                              size={14}
                            />
                          }
                          label="Room"
                          value={
                            stayContext.roomName
                          }
                        />
                      )}
                      {stayContext.checkIn && (
                        <ContextItem
                          icon={
                            <CalendarDays
                              size={14}
                            />
                          }
                          label="Check-in"
                          value={
                            formatDate(
                              stayContext.checkIn,
                            ) ??
                            stayContext.checkIn
                          }
                        />
                      )}
                      {stayContext.checkOut && (
                        <ContextItem
                          icon={
                            <CalendarDays
                              size={14}
                            />
                          }
                          label="Check-out"
                          value={
                            formatDate(
                              stayContext.checkOut,
                            ) ??
                            stayContext.checkOut
                          }
                        />
                      )}
                      {stayContext.guests && (
                        <ContextItem
                          icon={
                            <Users
                              size={14}
                            />
                          }
                          label="Guests"
                          value={`${stayContext.guests}`}
                        />
                      )}
                      {stayContext.estimatedTotal !==
                        undefined && (
                        <ContextItem
                          icon={
                            <Check
                              size={14}
                            />
                          }
                          label="Estimate"
                          value={
                            formatMoney(
                              stayContext.estimatedTotal,
                              stayContext.currency,
                            ) ?? "-"
                          }
                        />
                      )}
                    </div>
                  </div>
                )}
                {state === "error" &&
                  errorMessage && (
                    <div
                      className="
                        mx-auto
                        max-w-2xl
                        rounded-2xl
                        border
                        border-[#ff8e98]/20
                        bg-[#e85560]/10
                        px-4 py-3
                        text-center
                        text-xs
                        leading-5
                        text-[#ffc3c8]
                      "
                    >
                      {errorMessage}
                      <div className="mt-3 flex justify-center gap-2">
                        {supported && (
                          <button
                            type="button"
                            onClick={
                              handleOrbClick
                            }
                            className="
                              rounded-full
                              bg-white
                              px-4 py-2
                              text-xs
                              font-extrabold
                              text-[#173f76]
                            "
                          >
                            Try again
                          </button>
                        )}
                        <Link
                          href="/ai-concierge"
                          onClick={
                            endVoiceSession
                          }
                          className="
                            rounded-full
                            border
                            border-white/15
                            bg-white/10
                            px-4 py-2
                            text-xs
                            font-bold
                            text-white
                          "
                        >
                          Use chat
                        </Link>
                      </div>
                    </div>
                  )}
                {state ===
                  "unsupported" && (
                  <div
                    className="
                      mx-auto
                      max-w-xl
                      rounded-2xl
                      border
                      border-white/10
                      bg-white/[.05]
                      p-5 text-center
                    "
                  >
                    <AlertCircle
                      size={22}
                      className="mx-auto text-[#ffabb2]"
                    />
                    <p className="mt-3 text-sm font-bold">
                      Voice recognition
                      isn't available in
                      this browser.
                    </p>
                    <Link
                      href="/ai-concierge"
                      onClick={
                        endVoiceSession
                      }
                      className="
                        mt-4 inline-flex
                        rounded-full
                        bg-white
                        px-4 py-2.5
                        text-xs
                        font-extrabold
                        text-[#173f76]
                      "
                    >
                      Continue with chat
                    </Link>
                  </div>
                )}
              </div>
            </div>
          </div>
          <AnimatePresence>
            {showListeningOverlay && (
              <motion.div
                initial={{
                  opacity: 0,
                }}
                animate={{
                  opacity: 1,
                }}
                exit={{
                  opacity: 0,
                }}
                transition={{
                  duration: 0.2,
                }}
                className="
                  pointer-events-none
                  absolute inset-0 z-30
                  flex items-end
                  justify-center
                  bg-[#041126]/18
                  backdrop-blur-[2px]
                "
              >
                <motion.div
                  initial={{
                    y: 70,
                    opacity: 0,
                    scale: 0.9,
                  }}
                  animate={{
                    y: 0,
                    opacity: 1,
                    scale: 1,
                  }}
                  exit={{
                    y: 60,
                    opacity: 0,
                    scale: 0.92,
                  }}
                  transition={{
                    type: "spring",
                    stiffness: 280,
                    damping: 25,
                  }}
                  className="
                    pointer-events-auto
                    mb-24
                    flex w-full
                    max-w-xl
                    flex-col
                    items-center
                    px-5
                    text-center
                    sm:mb-28
                  "
                >
                  <AnimatePresence mode="wait">
                    <motion.div
                      key={
                        visibleTranscript ||
                        "listening"
                      }
                      initial={{
                        opacity: 0,
                        y: 8,
                      }}
                      animate={{
                        opacity: 1,
                        y: 0,
                      }}
                      exit={{
                        opacity: 0,
                        y: -5,
                      }}
                      className="
                        mb-4 max-w-xl
                        rounded-2xl
                        border border-white/10
                        bg-[#071a36]/55
                        px-4 py-2.5
                        shadow-[0_10px_40px_rgba(0,0,0,.18)]
                        backdrop-blur-md
                      "
                    >
                      {visibleTranscript ? (
                        <>
                          <p
                            className="
                              text-[9px]
                              font-extrabold
                              uppercase
                              tracking-[.18em]
                              text-[#87aaff]
                            "
                          >
                            I heard
                          </p>
                          <p
                            className="
                              mt-1 text-sm
                              font-semibold
                              leading-6
                              text-white
                              sm:text-base
                            "
                          >
                            “
                            {
                              visibleTranscript
                            }
                            ”
                          </p>
                        </>
                      ) : (
                        <p className="text-xs font-medium text-white/55">
                          Speak naturally…
                        </p>
                      )}
                    </motion.div>
                  </AnimatePresence>
                  <button
                    type="button"
                    onClick={
                      handleOrbClick
                    }
                    aria-label="Stop listening"
                    className="
                      relative grid
                      h-[88px] w-[88px]
                      place-items-center
                      rounded-full
                      outline-none
                      sm:h-[98px]
                      sm:w-[98px]
                    "
                  >
                    <motion.span
                      className="
                        absolute inset-0
                        rounded-full
                        border
                        border-[#76a0ff]/35
                        bg-[#5f7ff5]/10
                      "
                      animate={{
                        scale: [
                          1,
                          1.28,
                          1,
                        ],
                        opacity: [
                          0.7,
                          0.1,
                          0.7,
                        ],
                      }}
                      transition={{
                        duration: 1.7,
                        repeat: Infinity,
                        ease: "easeInOut",
                      }}
                    />
                    <motion.span
                      className="
                        absolute
                        inset-[10px]
                        rounded-full
                        border
                        border-[#70dfc1]/25
                      "
                      animate={{
                        scale: [
                          1,
                          1.34,
                          1,
                        ],
                        opacity: [
                          0.5,
                          0.08,
                          0.5,
                        ],
                      }}
                      transition={{
                        duration: 1.45,
                        repeat: Infinity,
                        ease: "easeInOut",
                        delay: 0.15,
                      }}
                    />
                    <motion.span
                      className="
                        absolute
                        inset-[17px]
                        rounded-full
                        bg-gradient-to-br
                        from-[#6c8cff]
                        via-[#6d78f2]
                        to-[#7d63ef]
                        shadow-[0_0_45px_rgba(103,130,255,.55)]
                      "
                      animate={{
                        scale: [
                          1,
                          1.07,
                          0.98,
                          1.05,
                          1,
                        ],
                        boxShadow: [
                          "0 0 30px rgba(103,130,255,.38)",
                          "0 0 58px rgba(103,130,255,.68)",
                          "0 0 38px rgba(93,221,190,.42)",
                          "0 0 55px rgba(103,130,255,.60)",
                          "0 0 30px rgba(103,130,255,.38)",
                        ],
                      }}
                      transition={{
                        duration: 1.25,
                        repeat: Infinity,
                        ease: "easeInOut",
                      }}
                    />
                    <Mic
                      size={23}
                      className="
                        relative z-10
                        text-white
                        drop-shadow-lg
                      "
                    />
                  </button>
                  <div className="mt-3 flex items-center gap-2">
                    <motion.span
                      className="
                        h-1.5 w-1.5
                        rounded-full
                        bg-[#69e0bb]
                      "
                      animate={{
                        opacity: [
                          0.35,
                          1,
                          0.35,
                        ],
                      }}
                      transition={{
                        duration: 1,
                        repeat: Infinity,
                      }}
                    />
                    <p
                      className="
                        text-[10px]
                        font-extrabold
                        uppercase
                        tracking-[.2em]
                        text-[#b7caff]
                      "
                    >
                      Listening
                    </p>
                  </div>
                </motion.div>
              </motion.div>
            )}
          </AnimatePresence>
        </section>
        {/*
          FLOATING CONTROLS
          Intentionally NO footer,
          toolbar background, container
          card or reserved bottom area.
        */}
        <div
          className="
            pointer-events-none
            absolute bottom-4
            left-1/2 z-50
            -translate-x-1/2
            sm:bottom-5
          "
        >
          <div
            className="
              pointer-events-auto
              flex items-center
              gap-2.5
            "
          >
            <button
              type="button"
              onClick={
                handleOrbClick
              }
              disabled={
                state ===
                "unsupported"
              }
              aria-label={
                voiceActive
                  ? "Turn off voice session"
                  : "Start hands-free voice session"
              }
              className={`
                grid h-11 w-11
                place-items-center
                rounded-full
                border
                shadow-[0_12px_35px_rgba(0,0,0,.28)]
                backdrop-blur-md
                transition
                ${
                  voiceActive
                    ? "border-[#7da8ff]/40 bg-[#36569a]/70 text-[#d6e2ff]"
                    : "border-white/15 bg-[#0a1d3b]/65 text-white hover:bg-[#102b55]/80"
                }
                disabled:cursor-not-allowed
                disabled:opacity-40
              `}
            >
              {voiceActive ? (
                <MicOff
                  size={17}
                />
              ) : (
                <Mic
                  size={17}
                />
              )}
            </button>
            <button
              type="button"
              onClick={
                toggleSpeaker
              }
              aria-label={
                speakerEnabled
                  ? "Turn off voice responses"
                  : "Turn on voice responses"
              }
              className={`
                grid h-11 w-11
                place-items-center
                rounded-full
                border
                shadow-[0_12px_35px_rgba(0,0,0,.28)]
                backdrop-blur-md
                transition
                ${
                  speakerEnabled
                    ? "border-white/15 bg-[#0a1d3b]/65 text-white hover:bg-[#102b55]/80"
                    : "border-[#e85560]/30 bg-[#612b3a]/70 text-[#ffabb2]"
                }
              `}
            >
              {speakerEnabled ? (
                <Volume2
                  size={17}
                />
              ) : (
                <VolumeX
                  size={17}
                />
              )}
            </button>
            <button
              type="button"
              onClick={
                resetConversation
              }
              disabled={
                state ===
                "thinking"
              }
              aria-label="Start a new voice conversation"
              className="
                grid h-11 w-11
                place-items-center
                rounded-full
                border border-white/15
                bg-[#0a1d3b]/65
                text-white
                shadow-[0_12px_35px_rgba(0,0,0,.28)]
                backdrop-blur-md
                transition
                hover:bg-[#102b55]/80
                disabled:cursor-not-allowed
                disabled:opacity-40
              "
            >
              <RotateCcw
                size={16}
              />
            </button>
            <Link
              href="/"
              onClick={
                endVoiceSession
              }
              className="
                flex h-11
                items-center gap-2
                rounded-full
                bg-[#e85560]
                px-4
                text-xs
                font-extrabold
                shadow-[0_12px_35px_rgba(0,0,0,.30)]
                transition
                hover:bg-[#f0646f]
              "
            >
              <PhoneOff
                size={16}
              />
              <span className="hidden sm:inline">
                End
              </span>
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
function ContextItem({
  icon,
  label,
  value,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center gap-2 rounded-xl border border-white/10 bg-white/[.04] px-3 py-2.5">
      <span className="text-[#9ebdff]">
        {icon}
      </span>
      <div className="min-w-0">
        <p className="text-[8px] font-extrabold uppercase tracking-[.12em] text-white/30">
          {label}
        </p>
        <p className="mt-0.5 truncate text-[10px] font-bold text-white/70">
          {value}
        </p>
      </div>
    </div>
  );
}
function VoiceResults({
  response,
}: {
  response: AiChatResponse;
}) {
  if (
    response.responseType ===
      "HOTELS" &&
    response.hotels?.length
  ) {
    return (
      <div className="space-y-2">
        <ResultHeading
          title="Hotel matches"
          detail={`${response.hotels.length} result${response.hotels.length === 1 ? "" : "s"} from SpatialStay`}
        />
        {response.hotels.map(
          (hotel) => (
            <Link
              key={hotel.id}
              href={`/hotels/${hotel.slug}`}
              className="group block rounded-2xl border border-white/10 bg-white/[.05] p-3.5 transition hover:border-white/20 hover:bg-white/[.08]"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-xs font-extrabold">
                    {hotel.name}
                  </p>
                  <div className="mt-1 flex items-center gap-1 text-[10px] text-white/40">
                    <MapPin
                      size={11}
                    />
                    <span className="truncate">
                      {
                        hotel.city
                      }
                    </span>
                  </div>
                </div>
                <ChevronRight
                  size={15}
                  className="mt-0.5 shrink-0 text-white/30 transition group-hover:translate-x-0.5 group-hover:text-white/60"
                />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <MiniPill>
                  ★{" "}
                  {hotel.rating.toFixed(
                    1,
                  )}
                </MiniPill>
                <MiniPill>
                  From{" "}
                  {formatMoney(
                    hotel.startingPrice,
                  )}
                </MiniPill>
              </div>
            </Link>
          ),
        )}
      </div>
    );
  }
  if (
    response.responseType ===
      "ROOMS" &&
    response.rooms?.length
  ) {
    return (
      <div className="space-y-2">
        <ResultHeading
          title="Room options"
          detail={`${response.rooms.length} room${response.rooms.length === 1 ? "" : "s"} returned`}
        />
        {response.rooms.map(
          (room) => (
            <div
              key={room.id}
              className="rounded-2xl border border-white/10 bg-white/[.05] p-3.5"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate text-xs font-extrabold">
                    {room.name}
                  </p>
                  <p className="mt-1 truncate text-[10px] text-white/40">
                    {
                      room.hotelName
                    }
                  </p>
                </div>
                <span className="shrink-0 text-[10px] font-extrabold text-[#c8d7ff]">
                  {formatMoney(
                    room.pricePerNight,
                    room.currency,
                  )}
                  /night
                </span>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <MiniPill>
                  Up to{" "}
                  {
                    room.maxGuests
                  }{" "}
                  guests
                </MiniPill>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <Link
                  href={`/rooms/${room.hotelSlug}/${room.slug}`}
                  className="rounded-full border border-white/10 bg-white/[.06] px-3 py-1.5 text-[9px] font-extrabold text-white/70 transition hover:bg-white/10"
                >
                  View room
                </Link>
                <Link
                  href={`/rooms/${room.hotelSlug}/${room.slug}/spatial`}
                  className="rounded-full border border-[#7298ff]/20 bg-[#7298ff]/10 px-3 py-1.5 text-[9px] font-extrabold text-[#b9cdff] transition hover:bg-[#7298ff]/15"
                >
                  Spatial view
                </Link>
              </div>
            </div>
          ),
        )}
      </div>
    );
  }
  if (
    response.responseType ===
      "AVAILABILITY" &&
    response.availability?.length
  ) {
    return (
      <div className="space-y-2">
        <ResultHeading
          title="Stay options"
          detail="Configured availability returned by SpatialStay"
        />
        {response.availability.map(
          (room) => (
            <div
              key={`${room.roomId}-${room.checkIn}-${room.checkOut}`}
              className="rounded-2xl border border-white/10 bg-white/[.05] p-3.5"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-extrabold">
                    {
                      room.roomName
                    }
                  </p>
                  <p className="mt-1 text-[10px] text-white/40">
                    {formatDate(
                      room.checkIn,
                    )}{" "}
                    →{" "}
                    {formatDate(
                      room.checkOut,
                    )}
                  </p>
                </div>
                <span
                  className={`shrink-0 rounded-full border px-2 py-1 text-[8px] font-extrabold ${
                    room.available
                      ? "border-[#61e1b0]/20 bg-[#61e1b0]/10 text-[#91efcd]"
                      : "border-[#e85560]/20 bg-[#e85560]/10 text-[#ffabb2]"
                  }`}
                >
                  {room.available
                    ? "Configured available"
                    : "Unavailable"}
                </span>
              </div>
              <div className="mt-3 grid grid-cols-2 gap-2 text-[9px]">
                <Stat
                  label="Stay"
                  value={`${room.nights} night${room.nights === 1 ? "" : "s"}`}
                />
                <Stat
                  label="Guests"
                  value={`${room.guests}`}
                />
                <Stat
                  label="Per night"
                  value={
                    formatMoney(
                      room.pricePerNight,
                      room.currency,
                    ) ?? "-"
                  }
                />
                <Stat
                  label="Estimate"
                  value={
                    formatMoney(
                      room.estimatedStayTotal,
                      room.currency,
                    ) ?? "-"
                  }
                />
              </div>
            </div>
          ),
        )}
      </div>
    );
  }
  return null;
}
function ResultHeading({
  title,
  detail,
}: {
  title: string;
  detail: string;
}) {
  return (
    <div className="flex items-end justify-between gap-3 px-1">
      <div>
        <p className="text-[9px] font-extrabold uppercase tracking-[.16em] text-[#a9c5ff]">
          {title}
        </p>
        <p className="mt-0.5 text-[9px] text-white/30">
          {detail}
        </p>
      </div>
    </div>
  );
}
function MiniPill({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <span className="rounded-full border border-white/10 bg-black/10 px-2.5 py-1 text-[8px] font-bold text-white/50">
      {children}
    </span>
  );
}
function Stat({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-xl border border-white/10 bg-black/10 px-3 py-2.5">
      <p className="text-[8px] font-extrabold uppercase tracking-[.12em] text-white/25">
        {label}
      </p>
      <p className="mt-1 font-bold text-white/65">
        {value}
      </p>
    </div>
  );
}

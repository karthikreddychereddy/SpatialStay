"use client";

import { motion } from "framer-motion";
import {
  BedDouble,
  Bot,
  Building2,
  Compass,
  Mic,
  Search,
  Sparkles,
  Star,
  WalletCards,
} from "lucide-react";
import Link from "next/link";
import {
  useEffect,
  useRef,
  useState,
} from "react";

import { AiComposer } from "@/components/ai/AiComposer";
import AiResultCards from "@/components/ai/AiResultCards";
import {
  AiMobileMenuButton,
  AiRecentChat,
  AiSidebar,
} from "@/components/ai/AiSidebar";
import {
  AiApiError,
  AiChatResponse,
  aiApi,
} from "@/lib/api/ai";

type Message = {
  id: number;
  role: "assistant" | "user";
  text: string;
  result?: AiChatResponse;
  error?: boolean;
};

const welcomePrompts = [
  {
    icon: <Compass size={17} />,
    title: "Find a stay",
    description:
      "Search SpatialStay by destination",
    prompt: "Show me stays in Goa.",
  },
  {
    icon: <WalletCards size={17} />,
    title: "Stay in budget",
    description:
      "Discover hotels under ₹10,000",
    prompt:
      "Find stays under ₹10,000 per night.",
  },
  {
    icon: <Star size={17} />,
    title: "Top rated",
    description:
      "See the strongest guest ratings",
    prompt:
      "Show me the highest-rated hotels.",
  },
  {
    icon: <BedDouble size={17} />,
    title: "Compare rooms",
    description:
      "Explore rooms at a specific hotel",
    prompt:
      "Help me find a hotel and compare its rooms.",
  },
];

export default function AIPage() {
  const [value, setValue] = useState("");
  const [thinking, setThinking] =
    useState(false);
  const [deepThink, setDeepThink] =
    useState(false);

  const [messages, setMessages] =
    useState<Message[]>([]);

  const [sidebarOpen, setSidebarOpen] =
    useState(false);
  const [
    sidebarCollapsed,
    setSidebarCollapsed,
  ] = useState(false);

  const [recentChats, setRecentChats] =
    useState<AiRecentChat[]>([]);

  const scrollRef =
    useRef<HTMLDivElement | null>(null);

  const hasConversation =
    messages.length > 0;

  useEffect(() => {
    const element = scrollRef.current;

    if (!element) {
      return;
    }

    element.scrollTo({
      top: element.scrollHeight,
      behavior: "smooth",
    });
  }, [messages, thinking]);

  async function send(text: string) {
    const clean = text.trim();

    if (!clean || thinking) {
      return;
    }

    const userMessage: Message = {
      id: createMessageId(),
      role: "user",
      text: clean,
    };

    setMessages((current) => [
      ...current,
      userMessage,
    ]);

    setRecentChats((current) =>
      rememberChat(current, clean),
    );

    setValue("");
    setThinking(true);

    try {
      const response =
        await aiApi.chat(clean);

      setMessages((current) => [
        ...current,
        {
          id: createMessageId(),
          role: "assistant",
          text: response.message,
          result: response,
        },
      ]);
    } catch (error) {
      let message =
        "I couldn't complete that request right now. Please try again.";

      if (error instanceof AiApiError) {
        if (error.status === 401) {
          message =
            "Please sign in to use Spatial AI.";
        } else if (
          error.status === 403
        ) {
          message =
            "Your secure session could not be verified. Please refresh the page and try again.";
        } else if (
          error.status === 503
        ) {
          message =
            "Spatial AI is temporarily unavailable. Please try again in a moment.";
        } else if (error.message) {
          message = error.message;
        }
      }

      setMessages((current) => [
        ...current,
        {
          id: createMessageId(),
          role: "assistant",
          text: message,
          error: true,
        },
      ]);
    } finally {
      setThinking(false);
    }
  }

  async function startNewConversation() {
    if (thinking) {
      return;
    }

    try {
      await aiApi.resetConversation();

      setMessages([]);
      setValue("");
      setDeepThink(false);
      setSidebarOpen(false);
    } catch (error) {
      if (
        error instanceof AiApiError &&
        error.status === 401
      ) {
        setMessages([
          {
            id: createMessageId(),
            role: "assistant",
            text:
              "Please sign in to start a Spatial AI conversation.",
            error: true,
          },
        ]);

        return;
      }

      setMessages((current) => [
        ...current,
        {
          id: createMessageId(),
          role: "assistant",
          text:
            "I couldn't start a new conversation right now.",
          error: true,
        },
      ]);
    }
  }

  function selectRecentChat(
    chat: AiRecentChat,
  ) {
    setSidebarOpen(false);

    /*
     * Recent chats are current-session labels only.
     * Persistent conversation restoration needs
     * backend chat-history support.
     */
    setValue(chat.title);
  }

  return (
    <main className="h-screen overflow-hidden bg-[#eef3f9] px-2 pb-2 pt-[76px] text-[#17385f] sm:px-4 sm:pb-4 sm:pt-[92px]">
      <section className="relative mx-auto flex h-full max-w-[1500px] overflow-hidden rounded-[26px] border border-white/90 bg-[#fbfcfe] shadow-[0_24px_80px_rgba(34,70,117,.13)] sm:h-[calc(100vh-108px)]">
        <AiSidebar
          open={sidebarOpen}
          collapsed={sidebarCollapsed}
          recentChats={recentChats}
          onClose={() =>
            setSidebarOpen(false)
          }
          onToggleCollapse={() =>
            setSidebarCollapsed(
              (current) => !current,
            )
          }
          onNewChat={() => {
            void startNewConversation();
          }}
          onSelectChat={
            selectRecentChat
          }
        />

        <div className="relative flex min-h-0 min-w-0 flex-1 flex-col bg-[radial-gradient(circle_at_50%_0%,rgba(231,239,255,.8),transparent_35%),#fbfcfe]">
          <header className="z-20 flex h-[64px] shrink-0 items-center justify-between border-b border-[#e7edf5] bg-white/78 px-3 backdrop-blur-xl sm:px-5">
            <div className="flex min-w-0 items-center gap-3">
              <AiMobileMenuButton
                onClick={() =>
                  setSidebarOpen(true)
                }
              />

              <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-[#3478ff] to-[#7668ef] text-white shadow-[0_7px_18px_rgba(60,99,222,.20)]">
                <Sparkles size={15} />
              </span>

              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h1 className="truncate text-sm font-black text-[#173b65]">
                    Spatial AI
                  </h1>

                  <span className="hidden rounded-full bg-[#edf3ff] px-2 py-0.5 text-[8px] font-black uppercase tracking-[.1em] text-[#5474aa] sm:inline">
                    Concierge
                  </span>
                </div>

                <p className="truncate text-[9px] font-semibold text-[#8c9cad] sm:text-[10px]">
                  Hotels, rooms and stay
                  planning
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              <div className="hidden items-center gap-1.5 rounded-full bg-[#edf8f4] px-2.5 py-1.5 text-[9px] font-extrabold text-[#3a826c] md:flex">
                <span className="h-1.5 w-1.5 rounded-full bg-[#3cb88c]" />
                Tools connected
              </div>

              <Link
                href="/ai-concierge/voice"
                className="flex items-center gap-2 rounded-full border border-[#d9e4f1] bg-white px-3 py-2 text-[10px] font-extrabold text-[#42668d] shadow-sm transition hover:bg-[#f4f8fd] sm:px-3.5"
              >
                <Mic size={14} />
                <span className="hidden sm:inline">
                  Talk to Agent
                </span>
              </Link>
            </div>
          </header>

          <div
            ref={scrollRef}
            className="hide-scrollbar min-h-0 flex-1 overflow-y-auto"
          >
            {!hasConversation ? (
              <WelcomeState
                onPrompt={(prompt) => {
                  void send(prompt);
                }}
              />
            ) : (
              <Conversation
                messages={messages}
                thinking={thinking}
                onPrompt={(prompt) => {
                  void send(prompt);
                }}
              />
            )}
          </div>

          <div className="z-30 shrink-0 border-t border-[#e8eef5] bg-white/82 px-3 py-3 backdrop-blur-2xl sm:px-5">
            <div className="mx-auto max-w-[820px]">
              <AiComposer
                value={value}
                thinking={thinking}
                deepThink={deepThink}
                onValueChange={setValue}
                onSubmit={() => {
                  void send(value);
                }}
                onToggleDeepThink={() =>
                  setDeepThink(
                    (current) => !current,
                  )
                }
              />

              <p className="mt-1.5 text-center text-[8px] leading-4 text-[#a0adba]">
                Spatial AI uses SpatialStay
                catalogue and room tools.
                Inventory is not a confirmed
                reservation until the booking
                engine is integrated.
              </p>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}

function WelcomeState({
  onPrompt,
}: {
  onPrompt: (prompt: string) => void;
}) {
  return (
    <div className="mx-auto flex min-h-full max-w-[900px] flex-col items-center justify-center px-5 py-12 text-center sm:px-8">
      <motion.div
        initial={{
          opacity: 0,
          scale: 0.9,
        }}
        animate={{
          opacity: 1,
          scale: 1,
        }}
        className="relative"
      >
        <div className="absolute inset-0 scale-[1.7] rounded-full bg-[#718cff]/10 blur-2xl" />

        <span className="relative grid h-14 w-14 place-items-center rounded-[18px] bg-gradient-to-br from-[#3478ff] to-[#7767ee] text-white shadow-[0_15px_40px_rgba(63,102,226,.25)]">
          <Sparkles size={22} />
        </span>
      </motion.div>

      <motion.div
        initial={{
          opacity: 0,
          y: 10,
        }}
        animate={{
          opacity: 1,
          y: 0,
        }}
        transition={{
          delay: 0.08,
        }}
      >
        <p className="mt-5 text-[10px] font-black uppercase tracking-[.18em] text-[#8295aa]">
          Spatial AI Concierge
        </p>

        <h2 className="mt-2 text-2xl font-black tracking-[-0.035em] text-[#173b65] sm:text-[34px]">
          Where would you like to stay?
        </h2>

        <p className="mx-auto mt-3 max-w-[540px] text-[12px] leading-6 text-[#73889f] sm:text-[13px]">
          Tell me naturally. I can search
          SpatialStay hotels, compare rooms
          and check configured inventory for
          your stay.
        </p>
      </motion.div>

      <div className="mt-8 grid w-full max-w-[680px] grid-cols-1 gap-2.5 sm:grid-cols-2">
        {welcomePrompts.map(
          (item, index) => (
            <motion.button
              key={item.title}
              type="button"
              onClick={() =>
                onPrompt(item.prompt)
              }
              initial={{
                opacity: 0,
                y: 10,
              }}
              animate={{
                opacity: 1,
                y: 0,
              }}
              transition={{
                delay:
                  0.12 + index * 0.05,
              }}
              className="group flex items-center gap-3 rounded-[18px] border border-[#e0e8f2] bg-white/82 p-3.5 text-left shadow-[0_8px_28px_rgba(40,76,121,.05)] transition hover:-translate-y-0.5 hover:border-[#ccdbee] hover:bg-white hover:shadow-[0_12px_32px_rgba(40,76,121,.09)]"
            >
              <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-[#f0f4ff] text-[#5b6fd1] transition group-hover:bg-[#e8edff]">
                {item.icon}
              </span>

              <span className="min-w-0">
                <span className="block text-[11px] font-black text-[#365878]">
                  {item.title}
                </span>

                <span className="mt-0.5 block truncate text-[9px] font-semibold text-[#91a0b0]">
                  {item.description}
                </span>
              </span>
            </motion.button>
          ),
        )}
      </div>

      <div className="mt-8 flex flex-wrap justify-center gap-x-5 gap-y-2 text-[9px] font-bold text-[#a0adbb]">
        <span className="flex items-center gap-1.5">
          <Building2 size={11} />
          Real SpatialStay catalogue
        </span>

        <span className="flex items-center gap-1.5">
          <Search size={11} />
          Natural-language search
        </span>

        <span className="flex items-center gap-1.5">
          <Bot size={11} />
          AI-assisted comparison
        </span>
      </div>
    </div>
  );
}

function Conversation({
  messages,
  thinking,
  onPrompt,
}: {
  messages: Message[];
  thinking: boolean;
  onPrompt: (prompt: string) => void;
}) {
  return (
    <div className="mx-auto w-full max-w-[900px] px-4 py-7 sm:px-7 sm:py-9">
      <div className="space-y-7">
        {messages.map((message) => {
          if (message.role === "user") {
            return (
              <UserMessage
                key={message.id}
                message={message}
              />
            );
          }

          return (
            <AssistantMessage
              key={message.id}
              message={message}
              onPrompt={onPrompt}
            />
          );
        })}

        {thinking ? (
          <ThinkingMessage />
        ) : null}
      </div>
    </div>
  );
}

function UserMessage({
  message,
}: {
  message: Message;
}) {
  return (
    <motion.div
      initial={{
        opacity: 0,
        y: 6,
      }}
      animate={{
        opacity: 1,
        y: 0,
      }}
      className="flex justify-end"
    >
      <div className="max-w-[85%] rounded-[20px] rounded-br-[6px] bg-[#173f76] px-4 py-2.5 text-[12px] leading-5 text-white shadow-[0_8px_22px_rgba(23,63,118,.13)] sm:max-w-[72%] sm:text-[13px]">
        {message.text}
      </div>
    </motion.div>
  );
}

function AssistantMessage({
  message,
  onPrompt,
}: {
  message: Message;
  onPrompt: (prompt: string) => void;
}) {
  return (
    <motion.div
      initial={{
        opacity: 0,
        y: 8,
      }}
      animate={{
        opacity: 1,
        y: 0,
      }}
      className="flex gap-3"
    >
      <span
        className={`mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-xl ${
          message.error
            ? "bg-[#fff0f0] text-[#bd6262]"
            : "bg-gradient-to-br from-[#3478ff] to-[#7668ef] text-white"
        }`}
      >
        <Sparkles size={13} />
      </span>

      <div className="min-w-0 flex-1">
        <p className="mb-1.5 text-[9px] font-black uppercase tracking-[.13em] text-[#8b9caf]">
          Spatial AI
        </p>

        <div
          className={`inline-block max-w-[680px] rounded-[17px] px-3.5 py-2.5 ${
            message.error
              ? "border border-[#f1dada] bg-[#fff7f7]"
              : "border border-[#e5ebf3] bg-white/72"
          }`}
        >
          <p
            className={`whitespace-pre-wrap text-[11px] leading-5 sm:text-[12px] ${
              message.error
                ? "text-[#a25d5d]"
                : "text-[#486680]"
            }`}
          >
            {message.text}
          </p>
        </div>

        {message.result ? (
          <AiResultCards
            hotels={
              message.result.hotels
            }
            rooms={message.result.rooms}
            availability={
              message.result.availability
            }
            onPrompt={onPrompt}
          />
        ) : null}
      </div>
    </motion.div>
  );
}

function ThinkingMessage() {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="flex gap-3"
    >
      <span className="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-[#3478ff] to-[#7668ef] text-white">
        <Sparkles size={13} />
      </span>

      <div>
        <p className="mb-1.5 text-[9px] font-black uppercase tracking-[.13em] text-[#8b9caf]">
          Spatial AI
        </p>

        <div className="flex items-center gap-3 rounded-[17px] border border-[#e4ebf3] bg-white/75 px-4 py-3">
          <div className="flex gap-1">
            {[0, 1, 2].map((item) => (
              <motion.span
                key={item}
                className="h-1.5 w-1.5 rounded-full bg-[#6e83a0]"
                animate={{
                  y: [0, -3, 0],
                }}
                transition={{
                  duration: 0.75,
                  repeat: Infinity,
                  delay: item * 0.1,
                }}
              />
            ))}
          </div>

          <span className="text-[10px] font-bold text-[#70869e]">
            Working on your request…
          </span>
        </div>
      </div>
    </motion.div>
  );
}

function rememberChat(
  current: AiRecentChat[],
  prompt: string,
): AiRecentChat[] {
  if (current.length > 0) {
    return current;
  }

  return [
    {
      id: `${Date.now()}`,
      title: shortenTitle(prompt),
    },
  ];
}

function shortenTitle(value: string) {
  const clean = value.trim();

  if (clean.length <= 34) {
    return clean;
  }

  return `${clean.slice(0, 31)}...`;
}

function createMessageId() {
  return (
    Date.now() +
    Math.floor(Math.random() * 1000)
  );
}
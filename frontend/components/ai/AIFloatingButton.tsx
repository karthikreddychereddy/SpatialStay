"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Bot, ChevronRight, MessageCircle, Mic, Sparkles, X } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useMemo, useState } from "react";

const promptsByArea = {
  home: ["Tell me your dream stay", "Talk to me — I can plan your trip", "I can find the right room for you"],
  hotels: ["Want me to compare these hotels?", "Tell me your budget and preferences", "I can narrow these stays in seconds"],
  hotel: ["Ask me anything about this hotel", "Want me to compare the rooms?", "I can help choose the best room"],
  room: ["Want me to help book this room?", "Ask me about this room", "I can compare this with another room"],
  trips: ["Need help with your stay?", "Ask me about your booking", "I can help manage your trip"],
};

export function AIFloatingButton() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [promptIndex, setPromptIndex] = useState(0);
  const [showPrompt, setShowPrompt] = useState(false);
  const [engaged, setEngaged] = useState(false);

  const prompts = useMemo(() => {
    if (pathname.startsWith("/rooms")) return promptsByArea.room;
    if (pathname.startsWith("/hotels/") && pathname !== "/hotels") return promptsByArea.hotel;
    if (pathname.startsWith("/hotels")) return promptsByArea.hotels;
    if (pathname.startsWith("/trips")) return promptsByArea.trips;
    return promptsByArea.home;
  }, [pathname]);

  useEffect(() => {
    setPromptIndex(0);
    setShowPrompt(false);
    if (engaged || open || pathname.startsWith("/ai-concierge")) return;

    let hideTimer: ReturnType<typeof setTimeout> | undefined;
    const reveal = () => {
      setShowPrompt(true);
      hideTimer = setTimeout(() => {
        setShowPrompt(false);
        setPromptIndex((current) => (current + 1) % prompts.length);
      }, 4000);
    };

    const firstTimer = setTimeout(reveal, 900);
    const interval = setInterval(reveal, 7000);
    return () => {
      clearTimeout(firstTimer);
      if (hideTimer) clearTimeout(hideTimer);
      clearInterval(interval);
    };
  }, [engaged, open, pathname, prompts.length]);

  if (pathname.startsWith("/ai-concierge") || pathname.startsWith("/hotel")) return null;

  const toggle = () => {
    setEngaged(true);
    setShowPrompt(false);
    setOpen((current) => !current);
  };

  return (
    <div className="pointer-events-none fixed bottom-5 right-4 z-[999] flex flex-col items-end sm:bottom-7 sm:right-7">
      <AnimatePresence>
        {showPrompt && !open && (
          <motion.button
            type="button"
            onClick={toggle}
            initial={{ opacity: 0, x: 18, y: 8, scale: 0.94 }}
            animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
            exit={{ opacity: 0, x: 12, y: 5, scale: 0.96 }}
            transition={{ type: "spring", stiffness: 360, damping: 28 }}
            className="pointer-events-auto mb-3 flex max-w-[310px] items-center gap-3 rounded-[22px] border border-white/90 bg-white/90 p-2.5 pr-3.5 text-left shadow-[0_20px_60px_rgba(24,61,112,.22)] backdrop-blur-2xl"
          >
            <span className="relative grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-gradient-to-br from-[#2d72ff] to-[#7768f8] text-white shadow-lg">
              <Sparkles size={17} />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-[10px] font-extrabold uppercase tracking-[.16em] text-[#6781a2]">Spatial AI</span>
              <span className="mt-0.5 block text-[13px] font-bold leading-5 text-[#173b68]">{prompts[promptIndex]}</span>
            </span>
            <ChevronRight size={15} className="shrink-0 text-[#7890ad]" />
          </motion.button>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: 16, scale: 0.94, filter: "blur(5px)" }}
            animate={{ opacity: 1, y: 0, scale: 1, filter: "blur(0px)" }}
            exit={{ opacity: 0, y: 12, scale: 0.96, filter: "blur(4px)" }}
            transition={{ type: "spring", stiffness: 350, damping: 30 }}
            className="pointer-events-auto mb-3 w-[min(340px,calc(100vw-32px))] overflow-hidden rounded-[28px] border border-white/90 bg-white/92 p-2 shadow-[0_28px_80px_rgba(24,61,112,.26)] backdrop-blur-3xl"
          >
            <div className="rounded-[22px] bg-gradient-to-br from-[#edf5ff] via-white to-[#f0efff] p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="flex gap-3">
                  <span className="grid h-11 w-11 place-items-center rounded-2xl bg-gradient-to-br from-[#2f73ff] to-[#766cf7] text-white shadow-lg"><Bot size={20} /></span>
                  <div><p className="text-[10px] font-extrabold uppercase tracking-[.17em] text-[#7187a3]">Spatial AI Agent</p><p className="mt-1 text-base font-extrabold text-[#153a68]">How can I help with your stay?</p></div>
                </div>
                <button onClick={() => setOpen(false)} className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-white/80 text-[#617895] shadow-sm" aria-label="Close Spatial AI"><X size={15} /></button>
              </div>
              <p className="mt-3 text-xs leading-5 text-[#6d829e]">Search, compare, explore rooms and prepare your booking through one conversation.</p>
            </div>
            <div className="p-1.5">
              <Link href="/ai-concierge" className="group mt-1 flex items-center gap-3 rounded-[20px] p-3 transition hover:bg-[#eef5ff]">
                <span className="grid h-12 w-12 place-items-center rounded-[17px] bg-[#e8f1ff] text-[#2f6df6]"><MessageCircle size={20} /></span>
                <span className="min-w-0 flex-1"><span className="block text-sm font-extrabold text-[#183b68]">Chat with Agent</span><span className="mt-0.5 block text-xs text-[#7186a2]">Plan naturally with rich stay results</span></span><ChevronRight size={16} className="text-[#8ca0b8] transition group-hover:translate-x-0.5" />
              </Link>
              <Link href="/ai-concierge/voice" className="group flex items-center gap-3 rounded-[20px] border border-[#dce7ff] bg-gradient-to-r from-[#f4f7ff] to-[#f7f3ff] p-3 shadow-sm transition hover:border-[#c8d8ff] hover:shadow-md">
                <span className="grid h-12 w-12 place-items-center rounded-[17px] bg-gradient-to-br from-[#3378ff] to-[#7769f7] text-white shadow-lg"><Mic size={20} /></span>
                <span className="min-w-0 flex-1"><span className="block text-sm font-extrabold text-[#163d75]">Talk to Agent</span><span className="mt-0.5 block text-xs font-medium text-[#5f7595]">Hands-free conversational planning</span></span><ChevronRight size={16} className="text-[#8ca0b8] transition group-hover:translate-x-0.5" />
              </Link>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <motion.button
        type="button"
        onClick={toggle}
        animate={{ y: [0, -3, 0] }}
        transition={{ duration: 3.4, repeat: Infinity, ease: "easeInOut" }}
        whileTap={{ scale: 0.96 }}
        className="pointer-events-auto relative flex h-[58px] items-center gap-2.5 rounded-full border border-white/90 bg-white/92 p-1.5 pr-4 shadow-[0_16px_50px_rgba(30,72,135,.24)] backdrop-blur-2xl sm:h-16 sm:gap-3 sm:p-2 sm:pr-5"
        aria-label="Open Spatial AI"
      >
        <span className="relative grid h-11 w-11 shrink-0 place-items-center rounded-full bg-gradient-to-br from-[#2f74ff] via-[#526ff5] to-[#8067ef] text-white shadow-[0_10px_28px_rgba(52,106,241,.38)] sm:h-12 sm:w-12">
          <motion.span className="absolute inset-[-5px] rounded-full border border-[#4b82f5]/20" animate={{ scale: [1, 1.1, 1], opacity: [.6, 0, .6] }} transition={{ duration: 2.8, repeat: Infinity }} />
          <Sparkles size={21} />
          <span className="absolute right-0 top-0 h-3.5 w-3.5 rounded-full border-[3px] border-white bg-[#35c98d]" />
        </span>
        <span className="text-left">
          <span className="block text-[10px] font-extrabold uppercase tracking-[.14em] text-[#7c91ab]">Your agent</span>
          <span className="block text-sm font-extrabold leading-4 text-[#173d70]">Spatial AI</span>
        </span>
      </motion.button>
    </div>
  );
}

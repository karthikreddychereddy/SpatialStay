"use client";

import {
  Brain,
  Mic,
  Plus,
  Send,
} from "lucide-react";
import Link from "next/link";
import {
  FormEvent,
  KeyboardEvent,
  useEffect,
  useRef,
} from "react";

type Props = {
  value: string;
  thinking: boolean;
  deepThink: boolean;
  onValueChange: (value: string) => void;
  onSubmit: () => void;
  onToggleDeepThink: () => void;
};

export function AiComposer({
  value,
  thinking,
  deepThink,
  onValueChange,
  onSubmit,
  onToggleDeepThink,
}: Props) {
  const textareaRef =
    useRef<HTMLTextAreaElement | null>(null);

  useEffect(() => {
    const textarea = textareaRef.current;

    if (!textarea) {
      return;
    }

    textarea.style.height = "24px";

    textarea.style.height = `${Math.min(
      Math.max(textarea.scrollHeight, 24),
      112,
    )}px`;
  }, [value]);

  function submit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    if (!thinking && value.trim()) {
      onSubmit();
    }
  }

  function handleKeyDown(
    event: KeyboardEvent<HTMLTextAreaElement>,
  ) {
    if (
      event.key === "Enter" &&
      !event.shiftKey
    ) {
      event.preventDefault();

      if (!thinking && value.trim()) {
        onSubmit();
      }
    }
  }

  const canSend =
    value.trim().length > 0 && !thinking;

  return (
    <form
      onSubmit={submit}
      className="
        mx-auto
        flex
        w-full
        items-end
        gap-1.5
        rounded-[24px]
        border
        border-[#d9e2ec]
        bg-white
        px-2
        py-2
        shadow-[0_8px_28px_rgba(31,64,105,.09)]
        transition
        duration-200
        focus-within:border-[#c6d5e7]
        focus-within:shadow-[0_10px_32px_rgba(31,64,105,.12)]
      "
    >
      {/* Add */}
      <button
        type="button"
        disabled
        aria-label="Add attachment"
        title="Attachments coming later"
        className="
          grid
          h-9
          w-9
          shrink-0
          cursor-default
          place-items-center
          rounded-full
          text-[#536d88]
          opacity-60
        "
      >
        <Plus size={18} />
      </button>

      {/* Message */}
      <div className="flex min-h-9 min-w-0 flex-1 items-center">
        <textarea
          ref={textareaRef}
          value={value}
          onChange={(event) =>
            onValueChange(event.target.value)
          }
          onKeyDown={handleKeyDown}
          disabled={thinking}
          rows={1}
          maxLength={4000}
          placeholder="Ask about hotels, rooms or your stay..."
          aria-label="Message Spatial AI"
          className="
            block
            max-h-28
            min-h-6
            w-full
            resize-none
            overflow-y-auto
            bg-transparent
            px-1
            py-0
            text-[13px]
            leading-6
            text-[#294761]
            outline-none
            placeholder:text-[#8d9cac]
            disabled:opacity-60
            sm:text-[14px]
          "
        />
      </div>

      {/* Think */}
      <button
        type="button"
        onClick={onToggleDeepThink}
        aria-pressed={deepThink}
        title="Deep Think"
        className={`
          flex
          h-9
          shrink-0
          items-center
          gap-1.5
          rounded-full
          px-2.5
          text-[10px]
          font-bold
          transition
          sm:px-3
          sm:text-[11px]
          ${
            deepThink
              ? "bg-[#eaf0ff] text-[#4164bc]"
              : "text-[#55708a] hover:bg-[#f1f4f8]"
          }
        `}
      >
        <Brain size={14} />

        <span className="hidden sm:inline">
          Think
        </span>
      </button>

      {/* Voice */}
      <Link
        href="/ai-concierge/voice"
        aria-label="Talk to Spatial AI"
        title="Talk to Spatial AI"
        className="
          grid
          h-9
          w-9
          shrink-0
          place-items-center
          rounded-full
          text-[#496783]
          transition
          hover:bg-[#f1f4f8]
        "
      >
        <Mic size={17} />
      </Link>

      {/* Send */}
      <button
        type="submit"
        disabled={!canSend}
        aria-label="Send message"
        className={`
          grid
          h-9
          w-9
          shrink-0
          place-items-center
          rounded-full
          transition
          ${
            canSend
              ? "bg-[#173f76] text-white shadow-[0_4px_12px_rgba(23,63,118,.20)] hover:bg-[#123662]"
              : "cursor-default bg-[#e3e8ee] text-[#9eabb8]"
          }
        `}
      >
        <Send size={14} />
      </button>
    </form>
  );
}
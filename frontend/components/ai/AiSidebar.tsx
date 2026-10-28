"use client";

import {
  Clock3,
  Compass,
  Heart,
  HelpCircle,
  Menu,
  MessageSquareText,
  PanelLeftClose,
  Plane,
  Plus,
  Search,
  Sparkles,
  X,
} from "lucide-react";
import Link from "next/link";

export type AiRecentChat = {
  id: string;
  title: string;
};

type Props = {
  open: boolean;
  collapsed: boolean;
  recentChats: AiRecentChat[];
  onClose: () => void;
  onToggleCollapse: () => void;
  onNewChat: () => void;
  onSelectChat: (chat: AiRecentChat) => void;
};

export function AiSidebar({
  open,
  collapsed,
  recentChats,
  onClose,
  onToggleCollapse,
  onNewChat,
  onSelectChat,
}: Props) {
  return (
    <>
      {open ? (
        <button
          type="button"
          aria-label="Close AI navigation"
          onClick={onClose}
          className="absolute inset-0 z-40 bg-[#0a2344]/20 backdrop-blur-[2px] xl:hidden"
        />
      ) : null}

      <aside
        className={`absolute inset-y-0 left-0 z-50 flex border-r border-[#e4ebf4] bg-[#f8faff]/96 shadow-[18px_0_55px_rgba(27,61,105,.08)] backdrop-blur-2xl transition-[width,transform] duration-300 xl:relative xl:translate-x-0 xl:shadow-none ${
          open
            ? "translate-x-0"
            : "-translate-x-full"
        } ${
          collapsed
            ? "w-[82px]"
            : "w-[270px]"
        }`}
      >
        <div className="flex min-w-0 flex-1 flex-col p-3">
          <div
            className={`flex h-12 items-center ${
              collapsed
                ? "justify-center"
                : "justify-between"
            }`}
          >
            {!collapsed ? (
              <div className="flex min-w-0 items-center gap-2.5 px-1">
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-gradient-to-br from-[#3478ff] to-[#7667ef] text-white shadow-[0_8px_20px_rgba(55,102,230,.25)]">
                  <Sparkles size={16} />
                </span>

                <div className="min-w-0">
                  <p className="truncate text-sm font-black text-[#173b65]">
                    Spatial AI
                  </p>
                  <p className="text-[10px] font-semibold text-[#8b9caf]">
                    Travel workspace
                  </p>
                </div>
              </div>
            ) : (
              <span className="grid h-9 w-9 place-items-center rounded-xl bg-gradient-to-br from-[#3478ff] to-[#7667ef] text-white">
                <Sparkles size={16} />
              </span>
            )}

            {!collapsed ? (
              <button
                type="button"
                onClick={onToggleCollapse}
                className="hidden h-8 w-8 place-items-center rounded-lg text-[#71869e] transition hover:bg-white hover:text-[#244d7c] xl:grid"
                aria-label="Collapse sidebar"
              >
                <PanelLeftClose size={16} />
              </button>
            ) : null}

            <button
              type="button"
              onClick={onClose}
              className="grid h-8 w-8 place-items-center rounded-lg text-[#71869e] xl:hidden"
              aria-label="Close sidebar"
            >
              <X size={17} />
            </button>
          </div>

          <button
            type="button"
            onClick={onNewChat}
            className={`mt-4 flex h-11 items-center rounded-xl bg-[#173f76] font-extrabold text-white shadow-[0_8px_24px_rgba(23,63,118,.18)] transition hover:bg-[#123661] ${
              collapsed
                ? "justify-center px-0"
                : "gap-2.5 px-3.5 text-xs"
            }`}
          >
            <Plus size={17} />
            {!collapsed ? "New chat" : null}
          </button>

          <div className="mt-5 space-y-1">
            {!collapsed ? (
              <SidebarLabel>
                Travel
              </SidebarLabel>
            ) : null}

            <SidebarLink
              href="/trips"
              icon={<Plane size={16} />}
              label="My Trips"
              collapsed={collapsed}
            />

            <SidebarButton
              icon={<Heart size={16} />}
              label="Saved stays"
              collapsed={collapsed}
              disabled
            />

            <SidebarButton
              icon={<Clock3 size={16} />}
              label="Recent searches"
              collapsed={collapsed}
              disabled
            />
          </div>

          <div className="mt-6 min-h-0 flex-1">
            {!collapsed ? (
              <>
                <SidebarLabel>
                  Recent chats
                </SidebarLabel>

                <div className="hide-scrollbar mt-1 max-h-[250px] space-y-1 overflow-y-auto">
                  {recentChats.length > 0 ? (
                    recentChats.map((chat) => (
                      <button
                        key={chat.id}
                        type="button"
                        onClick={() =>
                          onSelectChat(chat)
                        }
                        className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2.5 text-left text-[11px] font-semibold text-[#5c7390] transition hover:bg-white hover:text-[#173f76]"
                      >
                        <MessageSquareText
                          size={14}
                          className="shrink-0"
                        />

                        <span className="truncate">
                          {chat.title}
                        </span>
                      </button>
                    ))
                  ) : (
                    <div className="rounded-xl border border-dashed border-[#d9e4f1] px-3 py-4 text-center">
                      <p className="text-[10px] leading-4 text-[#96a6b8]">
                        Your current-session chats
                        will appear here.
                      </p>
                    </div>
                  )}
                </div>
              </>
            ) : (
              <div className="flex justify-center">
                <MessageSquareText
                  size={16}
                  className="text-[#8295aa]"
                />
              </div>
            )}
          </div>

          <div className="space-y-1 border-t border-[#e5ecf5] pt-3">
            <SidebarButton
              icon={<Compass size={16} />}
              label="AI preferences"
              collapsed={collapsed}
              disabled
            />

            <SidebarButton
              icon={<HelpCircle size={16} />}
              label="Help"
              collapsed={collapsed}
              disabled
            />

            <div
              className={`mt-2 flex items-center rounded-xl bg-[#edf7f4] text-[#3b816d] ${
                collapsed
                  ? "justify-center p-3"
                  : "gap-2.5 px-3 py-2.5"
              }`}
            >
              <span className="h-2 w-2 shrink-0 rounded-full bg-[#3ab88b]" />

              {!collapsed ? (
                <div>
                  <p className="text-[10px] font-extrabold">
                    Spatial tools connected
                  </p>
                  <p className="mt-0.5 text-[9px] text-[#6e998c]">
                    Hotels · rooms · inventory
                  </p>
                </div>
              ) : null}
            </div>
          </div>
        </div>
      </aside>
    </>
  );
}

export function AiMobileMenuButton({
  onClick,
}: {
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="grid h-9 w-9 place-items-center rounded-xl border border-[#dde7f3] bg-white text-[#55708e] xl:hidden"
      aria-label="Open AI navigation"
    >
      <Menu size={17} />
    </button>
  );
}

function SidebarLabel({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <p className="mb-1 px-3 text-[9px] font-black uppercase tracking-[.17em] text-[#9aa9ba]">
      {children}
    </p>
  );
}

function SidebarLink({
  href,
  icon,
  label,
  collapsed,
}: {
  href: string;
  icon: React.ReactNode;
  label: string;
  collapsed: boolean;
}) {
  return (
    <Link
      href={href}
      title={collapsed ? label : undefined}
      className={`flex items-center rounded-xl text-[#5d7591] transition hover:bg-white hover:text-[#173f76] ${
        collapsed
          ? "justify-center px-0 py-3"
          : "gap-2.5 px-3 py-2.5 text-xs font-bold"
      }`}
    >
      {icon}
      {!collapsed ? label : null}
    </Link>
  );
}

function SidebarButton({
  icon,
  label,
  collapsed,
  disabled = false,
}: {
  icon: React.ReactNode;
  label: string;
  collapsed: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      title={collapsed ? label : undefined}
      className={`flex w-full items-center rounded-xl text-[#5d7591] transition ${
        collapsed
          ? "justify-center px-0 py-3"
          : "gap-2.5 px-3 py-2.5 text-left text-xs font-bold"
      } ${
        disabled
          ? "cursor-default opacity-55"
          : "hover:bg-white hover:text-[#173f76]"
      }`}
    >
      {icon}
      {!collapsed ? (
        <>
          <span>{label}</span>

          {disabled ? (
            <span className="ml-auto rounded-full bg-[#edf2f7] px-1.5 py-0.5 text-[7px] font-black uppercase tracking-wide text-[#9aa8b7]">
              Soon
            </span>
          ) : null}
        </>
      ) : null}
    </button>
  );
}
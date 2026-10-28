"use client";

import { motion } from "framer-motion";
import {
  ChevronDown,
  Headphones,
  LogOut,
  Menu,
  Plane,
  UserRound,
  X,
} from "lucide-react";
import Link from "next/link";
import {
  usePathname,
  useRouter,
} from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";

import { AuthModal } from "@/components/auth/AuthModal";
import {
  AuthUser,
  authApi,
} from "@/lib/auth-api";

const links = [
  { href: "/", label: "Home" },
  { href: "/hotels", label: "Hotels" },
  { href: "/ai-concierge", label: "AI Agent" },
  { href: "/trips", label: "My Trips" },
];

export function Navbar() {
  const pathname = usePathname();
  const router = useRouter();

  const [open, setOpen] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);

  const [user, setUser] = useState<AuthUser | null>(null);
  const [sessionLoading, setSessionLoading] = useState(true);
  const [loggingOut, setLoggingOut] = useState(false);

  const accountRef = useRef<HTMLDivElement | null>(null);

  const [verifyingEmail, setVerifyingEmail] = useState(false);
  const [verificationMessage, setVerificationMessage] = useState("");

  const loadSession = useCallback(async () => {
    try {
      const currentUser = await authApi.me();
      setUser(currentUser);
    } catch {
      /*
       * A temporary backend/network problem should not crash
       * navigation. Authentication actions can surface errors later.
       */
      setUser(null);
    } finally {
      setSessionLoading(false);
    }
  }, []);
  useEffect(() => {
    const url = new URL(window.location.href);
    const authResult = url.searchParams.get("auth");

    if (!authResult) {
      return;
    }

    if (authResult === "success") {
      void loadSession();
    }

    /*
    * OAuth result is temporary transport state.
    * Remove it without causing another navigation/render.
    */
    url.searchParams.delete("auth");

    const cleanUrl =
      url.pathname +
      (url.search ? url.search : "") +
      url.hash;

    window.history.replaceState(
      window.history.state,
      "",
      cleanUrl,
    );
  }, [loadSession]);

  useEffect(() => {
    void loadSession();
  }, [loadSession]);

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      if (
        accountRef.current &&
        !accountRef.current.contains(event.target as Node)
      ) {
        setAccountOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
    };
  }, []);

  function isActive(href: string) {
    return href === "/"
      ? pathname === "/"
      : pathname.startsWith(href);
  }

  function openAuthentication() {
    setOpen(false);
    setAccountOpen(false);
    setAuthOpen(true);
  }

  function handleAuthenticated(authenticatedUser: AuthUser) {
    setUser(authenticatedUser);
    setSessionLoading(false);
  }

  async function handleLogout() {
    if (loggingOut) {
      return;
    }

    setLoggingOut(true);

    try {
      await authApi.logout();

      /*
      * Remove customer identity from the UI only after the
      * backend confirms that the server session was destroyed.
      */
      setUser(null);
      setAccountOpen(false);
      setOpen(false);
      setAuthOpen(false);

      /*
      * Never leave an anonymous visitor sitting on a
      * customer-only route such as My Trips.
      */
      router.replace("/");
      router.refresh();
    } catch (error) {
      console.error("SpatialStay logout failed.", error);

      /*
      * Do not pretend logout succeeded. Re-read the authoritative
      * server session if logout failed.
      */
      await loadSession();
    } finally {
      setLoggingOut(false);
    }
  }
  async function handleVerifyEmail() {
    if (!user || user.emailVerified || verifyingEmail) {
      return;
    }

    setVerifyingEmail(true);
    setVerificationMessage("");

    try {
      const request =
        await authApi.requestEmailVerification();

      /*
      * Development only.
      *
      * Production will send the token through email and the
      * verification link will call the confirmation endpoint.
      */
      if (request.developmentToken) {
        await authApi.confirmEmailVerification(
          request.developmentToken,
        );

        const refreshedUser = await authApi.me();

        if (refreshedUser) {
          setUser(refreshedUser);
        }

        setVerificationMessage("Email verified");
        return;
      }

      setVerificationMessage(
        "Verification email sent. Check your inbox.",
      );
    } catch (error) {
      console.error(
        "SpatialStay email verification failed.",
        error,
      );

      setVerificationMessage(
        "Unable to verify email right now.",
      );
    } finally {
      setVerifyingEmail(false);
    }
  }

  const displayName = user
    ? user.firstName?.trim() || user.email
    : "";

  return (
    <>
      <header className="fixed inset-x-0 top-0 z-50 px-3 pt-3 sm:px-5 sm:pt-4">
        <div className="nav-glass mx-auto max-w-[1480px] rounded-2xl px-3 py-2.5 sm:rounded-[24px] sm:px-4">
          <div className="flex items-center justify-between gap-3">
            <Link
              href="/"
              className="flex shrink-0 items-center gap-2 font-bold text-[#0b2857]"
            >
              <span className="grid h-9 w-9 place-items-center rounded-full bg-gradient-to-br from-[#3378ff] to-[#6a7cf5] text-white shadow-lg">
                <Plane size={18} />
              </span>

              <span className="text-lg sm:text-xl">
                SpatialStay
              </span>
            </Link>

            <nav className="relative hidden items-center gap-1 rounded-[18px] border border-white/80 bg-white/28 p-1 shadow-[inset_0_1px_0_rgba(255,255,255,.9)] backdrop-blur-xl lg:flex">
              {links.map((link) => {
                const active = isActive(link.href);

                return (
                  <Link
                    key={link.href}
                    href={link.href}
                    aria-current={active ? "page" : undefined}
                    className="relative isolate min-w-[88px] rounded-[14px] px-4 py-2 text-center text-sm font-semibold text-[#49627f]"
                  >
                    {active && (
                      <motion.span
                        layoutId="active-navigation-glass"
                        className="absolute inset-0 -z-10 rounded-[14px] border border-white/95 bg-white/72 shadow-[0_8px_26px_rgba(43,70,112,.13),inset_0_1px_0_rgba(255,255,255,1)] backdrop-blur-2xl"
                        transition={{
                          type: "spring",
                          stiffness: 360,
                          damping: 32,
                          mass: 0.72,
                        }}
                      />
                    )}

                    <span
                      className={
                        active
                          ? "text-[#173d70]"
                          : "transition-colors duration-200 hover:text-[#173d70]"
                      }
                    >
                      {link.label}
                    </span>
                  </Link>
                );
              })}
            </nav>

            <div className="hidden items-center gap-2 sm:flex">
              <Link href="/hotel-partner" className="rounded-xl px-3 py-2 text-sm font-bold text-[#315f9c] transition hover:bg-white/70">List your property</Link>
              <button
                type="button"
                className="flex items-center gap-2 rounded-xl border border-[#c9daf1] bg-white/64 px-3 py-2 text-sm font-semibold text-[#46617f] transition hover:bg-white/90"
              >
                <Headphones size={16} />
                Support
              </button>

              {!sessionLoading && !user && (
                <button
                  type="button"
                  onClick={openAuthentication}
                  className="blue-button rounded-xl px-4 py-2 text-sm font-bold transition hover:-translate-y-0.5"
                >
                  Login / Sign Up
                </button>
              )}

              {!sessionLoading && user && (
                <div
                  ref={accountRef}
                  className="relative"
                >
                  <button
                    type="button"
                    onClick={() =>
                      setAccountOpen((value) => !value)
                    }
                    className="flex items-center gap-2 rounded-xl border border-[#c9daf1] bg-white/75 px-2.5 py-2 text-sm font-bold text-[#173d70] shadow-sm transition hover:bg-white"
                    aria-expanded={accountOpen}
                  >
                    <span className="grid h-7 w-7 place-items-center rounded-full bg-gradient-to-br from-[#3378ff] to-[#6a7cf5] text-white">
                      <UserRound size={14} />
                    </span>

                    <span className="max-w-[120px] truncate">
                      {displayName}
                    </span>

                    <ChevronDown
                      size={15}
                      className={`transition ${
                        accountOpen ? "rotate-180" : ""
                      }`}
                    />
                  </button>

                  {accountOpen && (
                    <div className="absolute right-0 mt-2 w-72 overflow-hidden rounded-2xl border border-[#d7e4f4] bg-white p-2 shadow-[0_20px_60px_rgba(20,52,94,.18)]">
                      <div className="rounded-xl bg-[#f3f7fd] px-3 py-3">
                        <p className="truncate text-sm font-bold text-[#173d70]">
                          {user.firstName} {user.lastName}
                        </p>

                        <p className="mt-1 truncate text-xs text-[#7187a2]">
                          {user.email}
                        </p>

                        {!user.emailVerified && (
                          <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-2.5">
                            <p className="text-xs font-semibold text-amber-800">
                              Email verification pending
                            </p>

                            <button
                              type="button"
                              onClick={() => void handleVerifyEmail()}
                              disabled={verifyingEmail}
                              className="mt-2 text-xs font-bold text-[#315f9c] hover:underline disabled:opacity-50"
                            >
                              {verifyingEmail
                                ? "Verifying..."
                                : "Verify Email"}
                            </button>
                          </div>
                        )}

                        {verificationMessage && (
                          <p className="mt-2 text-xs font-semibold text-emerald-700">
                            {verificationMessage}
                          </p>
                        )}
                      </div>

                      <Link
                        href="/trips"
                        onClick={() => setAccountOpen(false)}
                        className="mt-1 flex items-center gap-2 rounded-xl px-3 py-2.5 text-sm font-semibold text-[#46617f] transition hover:bg-[#f3f7fd] hover:text-[#173d70]"
                      >
                        <Plane size={16} />
                        My Trips
                      </Link>

                      <button
                        type="button"
                        onClick={() => void handleLogout()}
                        disabled={loggingOut}
                        className="flex w-full items-center gap-2 rounded-xl px-3 py-2.5 text-left text-sm font-semibold text-[#46617f] transition hover:bg-red-50 hover:text-red-700 disabled:opacity-50"
                      >
                        <LogOut size={16} />
                        {loggingOut
                          ? "Signing out..."
                          : "Sign Out"}
                      </button>
                    </div>
                  )}
                </div>
              )}

              {sessionLoading && (
                <div className="h-10 w-32 animate-pulse rounded-xl bg-white/55" />
              )}
            </div>

            <button
              type="button"
              onClick={() => setOpen((value) => !value)}
              className="grid h-10 w-10 place-items-center rounded-xl border border-[#c7daf5] bg-white/75 lg:hidden"
              aria-label="Toggle navigation"
              aria-expanded={open}
            >
              {open ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>

          {open && (
            <div className="mt-3 border-t border-[#d9e6f7] pt-3 lg:hidden">
              <div className="grid gap-1">
                {links.map((link) => {
                  const active = isActive(link.href);

                  return (
                    <Link
                      key={link.href}
                      href={link.href}
                      onClick={() => setOpen(false)}
                      aria-current={
                        active ? "page" : undefined
                      }
                      className={`rounded-xl border px-3 py-3 text-sm font-semibold transition ${
                        active
                          ? "border-white bg-white/80 text-[#173d70] shadow-[0_7px_20px_rgba(45,76,126,.10)]"
                          : "border-transparent text-[#3f5879] hover:bg-white/70"
                      }`}
                    >
                      {link.label}
                    </Link>
                  );
                })}

                <Link href="/hotel-partner" onClick={() => setOpen(false)} className="rounded-xl border border-transparent px-3 py-3 text-sm font-bold text-[#315f9c] hover:bg-white/70">List your property</Link>

                {!sessionLoading && !user && (
                  <button
                    type="button"
                    onClick={openAuthentication}
                    className="blue-button mt-2 rounded-xl px-4 py-3 text-center text-sm font-bold"
                  >
                    Login / Sign Up
                  </button>
                )}

                {!sessionLoading && user && (
                  <div className="mt-2 rounded-2xl border border-[#d4e2f3] bg-white/70 p-2">
                    <div className="px-2 py-2">
                      <p className="truncate text-sm font-bold text-[#173d70]">
                        {user.firstName} {user.lastName}
                      </p>

                      <p className="mt-1 truncate text-xs text-[#7287a1]">
                        {user.email}
                      </p>
                      {!user.emailVerified && (
                        <div className="mt-3 rounded-xl border border-amber-200 bg-amber-50 p-2.5">
                          <p className="text-xs font-semibold text-amber-800">
                            Email verification pending
                          </p>

                          <button
                            type="button"
                            onClick={() => void handleVerifyEmail()}
                            disabled={verifyingEmail}
                            className="mt-2 text-xs font-bold text-[#315f9c] disabled:opacity-50"
                          >
                            {verifyingEmail ? "Verifying..." : "Verify Email"}
                          </button>
                        </div>
                      )}

                      {verificationMessage && (
                        <p className="mt-2 text-xs font-semibold text-emerald-700">
                          {verificationMessage}
                        </p>
                      )}
                    </div>

                    <button
                      type="button"
                      onClick={() => void handleLogout()}
                      disabled={loggingOut}
                      className="mt-1 flex w-full items-center justify-center gap-2 rounded-xl border border-[#d9e5f3] bg-white px-4 py-3 text-sm font-bold text-[#47617f]"
                    >
                      <LogOut size={16} />
                      {loggingOut
                        ? "Signing out..."
                        : "Sign Out"}
                    </button>
                  </div>
                )}

                {sessionLoading && (
                  <div className="mt-2 h-11 animate-pulse rounded-xl bg-white/55" />
                )}
              </div>
            </div>
          )}
        </div>
      </header>

      <AuthModal
        open={authOpen}
        onClose={() => setAuthOpen(false)}
        onAuthenticated={handleAuthenticated}
      />
    </>
  );
}
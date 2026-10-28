"use client";

import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowLeft,
  Check,
  Eye,
  EyeOff,
  KeyRound,
  LoaderCircle,
  LockKeyhole,
  Mail,
  ShieldCheck,
  Sparkles,
  UserRound,
  X,
} from "lucide-react";
import {
  FormEvent,
  ReactNode,
  useEffect,
  useMemo,
  useState,
} from "react";

import {
  AuthApiError,
  AuthUser,
  authApi,
} from "@/lib/auth-api";

type AuthMode =
  | "signin"
  | "signup"
  | "forgot"
  | "reset"
  | "recovery-success";

type AuthModalProps = {
  open: boolean;
  onClose: () => void;
  onAuthenticated: (user: AuthUser) => void;
};

type FieldErrors = Record<string, string>;

export function AuthModal({
  open,
  onClose,
  onAuthenticated,
}: AuthModalProps) {
  const [mode, setMode] = useState<AuthMode>("signin");

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");

  const [password, setPassword] = useState("");
  const [resetPassword, setResetPassword] = useState("");
  const [resetToken, setResetToken] = useState("");

  const [showPassword, setShowPassword] = useState(false);
  const [showResetPassword, setShowResetPassword] =
    useState(false);

  const [submitting, setSubmitting] = useState(false);

  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [fieldErrors, setFieldErrors] =
    useState<FieldErrors>({});

  useEffect(() => {
    if (!open) {
      return;
    }

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape" && !submitting) {
        onClose();
      }
    }

    window.addEventListener("keydown", handleEscape);

    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", handleEscape);
    };
  }, [open, onClose, submitting]);

  const signupPasswordChecks = useMemo(
    () => passwordRules(password),
    [password],
  );

  const resetPasswordChecks = useMemo(
    () => passwordRules(resetPassword),
    [resetPassword],
  );

  function clearFeedback() {
    setError("");
    setMessage("");
    setFieldErrors({});
  }

  function switchMode(nextMode: AuthMode) {
    clearFeedback();
    setMode(nextMode);
  }

  function handleApiError(apiError: unknown) {
    if (apiError instanceof AuthApiError) {
      setError(apiError.message);
      setFieldErrors(apiError.fields ?? {});
      return;
    }

    setError(
      "We couldn't reach SpatialStay. Please try again.",
    );
  }

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault();

    clearFeedback();
    setSubmitting(true);

    try {
      if (mode === "signin") {
        const user = await authApi.login({
          email,
          password,
        });

        onAuthenticated(user);
        onClose();
        return;
      }

      if (mode === "signup") {
        const user = await authApi.register({
          firstName,
          lastName,
          email,
          password,
        });

        setEmail(user.email);
        setPassword("");
        setMode("signin");
        setMessage(
          `Welcome ${user.firstName}. Your account has been created. Sign in to continue.`,
        );
        return;
      }

      if (mode === "forgot") {
        const result =
          await authApi.forgotPassword(email);

        /*
         * Development only:
         * the backend exposes the raw reset token while
         * app.auth.expose-development-tokens=true.
         *
         * Production will instead deliver the reset URL
         * through the email provider.
         */
        if (result.developmentToken) {
          setResetToken(result.developmentToken);
          setResetPassword("");
          setMode("reset");
          setMessage(
            "Development recovery request accepted. Create your new password.",
          );
          return;
        }

        setMode("recovery-success");
        setMessage(result.message);
        return;
      }

      if (mode === "reset") {
        if (!resetToken) {
          setError(
            "The password reset link is missing or invalid.",
          );
          return;
        }

        await authApi.resetPassword(
          resetToken,
          resetPassword,
        );

        setResetToken("");
        setResetPassword("");
        setPassword("");
        setMode("signin");

        setMessage(
          "Your password has been reset successfully. Sign in with your new password.",
        );
      }
    } catch (apiError) {
      handleApiError(apiError);
    } finally {
      setSubmitting(false);
    }
  }

  function continueWithGoogle() {
    window.location.assign(authApi.googleLoginUrl());
  }

  const title =
    mode === "signin"
      ? "Continue your journey"
      : mode === "signup"
        ? "Join SpatialStay"
        : mode === "forgot"
          ? "Reset your password"
          : mode === "reset"
            ? "Create a new password"
            : "Check your email";

  const eyebrow =
    mode === "signin"
      ? "Welcome back"
      : mode === "signup"
        ? "Create your account"
        : "Secure recovery";

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="fixed inset-0 z-[100] flex items-end justify-center bg-[#07152b]/45 p-0 backdrop-blur-md sm:items-center sm:p-5"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onMouseDown={(event) => {
            if (
              event.target === event.currentTarget &&
              !submitting
            ) {
              onClose();
            }
          }}
        >
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-labelledby="spatialstay-auth-title"
            initial={{
              opacity: 0,
              y: 34,
              scale: 0.985,
            }}
            animate={{
              opacity: 1,
              y: 0,
              scale: 1,
            }}
            exit={{
              opacity: 0,
              y: 20,
              scale: 0.99,
            }}
            transition={{
              type: "spring",
              stiffness: 310,
              damping: 28,
            }}
            className="relative max-h-[94dvh] w-full overflow-y-auto rounded-t-[32px] border border-white/70 bg-[#f8fbff] shadow-[0_35px_120px_rgba(5,24,55,.3)] sm:max-w-[920px] sm:rounded-[32px]"
          >
            <button
              type="button"
              onClick={onClose}
              disabled={submitting}
              aria-label="Close authentication"
              className="absolute right-4 top-4 z-20 grid h-10 w-10 place-items-center rounded-full border border-[#d9e6f7] bg-white/90 text-[#47617f] shadow-sm transition hover:bg-white disabled:opacity-50"
            >
              <X size={18} />
            </button>

            <div className="grid lg:grid-cols-[0.88fr_1.12fr]">
              <section className="relative hidden min-h-[650px] overflow-hidden bg-gradient-to-br from-[#0d2d62] via-[#174d98] to-[#6687ff] p-9 text-white lg:block">
                <div className="absolute -left-20 top-28 h-64 w-64 rounded-full bg-white/10 blur-2xl" />
                <div className="absolute -bottom-20 -right-16 h-72 w-72 rounded-full bg-[#7ee7ff]/20 blur-3xl" />

                <div className="relative flex h-full flex-col">
                  <div className="flex items-center gap-2 font-bold">
                    <span className="grid h-10 w-10 place-items-center rounded-full bg-white/15 ring-1 ring-white/25">
                      <Sparkles size={18} />
                    </span>
                    SpatialStay
                  </div>

                  <div className="my-auto">
                    <p className="text-xs font-bold uppercase tracking-[0.24em] text-[#bcd5ff]">
                      One secure identity
                    </p>

                    <h2 className="mt-4 max-w-sm text-4xl font-bold leading-tight">
                      Your stays, conversations and trips
                      stay connected.
                    </h2>

                    <p className="mt-5 max-w-sm text-sm leading-7 text-white/72">
                      Sign in once to continue bookings,
                      manage future trips and use
                      SpatialStay securely.
                    </p>

                    <div className="mt-8 space-y-4">
                      <Feature
                        icon={<ShieldCheck size={17} />}
                        title="Secure sessions"
                        description="Authentication is controlled by the SpatialStay backend."
                      />

                      <Feature
                        icon={<Check size={17} />}
                        title="Verified identity"
                        description="Bookings and trips stay attached to the correct customer."
                      />

                      <Feature
                        icon={<Sparkles size={17} />}
                        title="AI-ready"
                        description="Future AI actions remain bound to your authenticated permissions."
                      />
                    </div>
                  </div>
                </div>
              </section>

              <section className="p-5 pb-8 pt-16 sm:p-8 sm:pt-14 lg:p-10 lg:pt-14">
                <div className="mx-auto max-w-md">
                  {mode === "forgot" ||
                  mode === "reset" ||
                  mode === "recovery-success" ? (
                    <button
                      type="button"
                      onClick={() =>
                        switchMode("signin")
                      }
                      className="mb-6 flex items-center gap-2 text-sm font-semibold text-[#52709a] transition hover:text-[#173d70]"
                    >
                      <ArrowLeft size={16} />
                      Back to sign in
                    </button>
                  ) : (
                    <div className="mb-7 grid grid-cols-2 rounded-2xl border border-[#d9e6f7] bg-[#edf4fd] p-1">
                      <ModeButton
                        active={mode === "signin"}
                        onClick={() =>
                          switchMode("signin")
                        }
                      >
                        Sign In
                      </ModeButton>

                      <ModeButton
                        active={mode === "signup"}
                        onClick={() =>
                          switchMode("signup")
                        }
                      >
                        Sign Up
                      </ModeButton>
                    </div>
                  )}

                  <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6683aa]">
                    {eyebrow}
                  </p>

                  <h2
                    id="spatialstay-auth-title"
                    className="mt-2 text-3xl font-bold tracking-tight text-[#0b2857]"
                  >
                    {title}
                  </h2>

                  <p className="mt-2 text-sm leading-6 text-[#637b99]">
                    {mode === "signin" &&
                      "Access your trips, bookings and personalized SpatialStay experience."}

                    {mode === "signup" &&
                      "Create your secure customer account in just a few seconds."}

                    {mode === "forgot" &&
                      "Enter your account email to begin secure password recovery."}

                    {mode === "reset" &&
                      "Choose a new password for your SpatialStay account."}

                    {mode === "recovery-success" &&
                      "If the account exists, password recovery instructions have been sent."}
                  </p>

                  {(mode === "signin" ||
                    mode === "signup") && (
                    <>
                      <button
                        type="button"
                        onClick={continueWithGoogle}
                        disabled={submitting}
                        className="mt-6 flex w-full items-center justify-center gap-3 rounded-2xl border border-[#cddcf0] bg-white px-4 py-3.5 text-sm font-bold text-[#243f65] shadow-[0_8px_24px_rgba(36,63,101,.06)] transition hover:-translate-y-0.5 hover:border-[#aec8ea] disabled:opacity-60"
                      >
                        <GoogleMark />
                        Continue with Google
                      </button>

                      <div className="my-6 flex items-center gap-3 text-[11px] font-bold uppercase tracking-[0.18em] text-[#9aacbf]">
                        <span className="h-px flex-1 bg-[#dce7f4]" />
                        or continue with email
                        <span className="h-px flex-1 bg-[#dce7f4]" />
                      </div>
                    </>
                  )}

                  {mode === "recovery-success" ? (
                    <div className="mt-7 rounded-3xl border border-[#cfe3d8] bg-[#f2fbf6] p-6 text-center">
                      <span className="mx-auto grid h-12 w-12 place-items-center rounded-full bg-emerald-100 text-emerald-700">
                        <Mail size={21} />
                      </span>

                      <p className="mt-4 text-sm leading-6 text-[#547062]">
                        {message}
                      </p>

                      <button
                        type="button"
                        onClick={() =>
                          switchMode("signin")
                        }
                        className="blue-button mt-5 w-full rounded-2xl px-5 py-3.5 text-sm font-bold"
                      >
                        Return to Sign In
                      </button>
                    </div>
                  ) : (
                    <form
                      onSubmit={handleSubmit}
                      noValidate
                    >
                      {mode === "signup" && (
                        <div className="mt-6 grid gap-4 sm:grid-cols-2">
                          <Field
                            label="First name"
                            error={
                              fieldErrors.firstName
                            }
                          >
                            <UserRound size={17} />

                            <input
                              value={firstName}
                              onChange={(event) =>
                                setFirstName(
                                  event.target.value,
                                )
                              }
                              autoComplete="given-name"
                              placeholder="First name"
                              className="w-full bg-transparent text-sm text-[#173d70] outline-none placeholder:text-[#9badc1]"
                            />
                          </Field>

                          <Field
                            label="Last name"
                            error={
                              fieldErrors.lastName
                            }
                          >
                            <UserRound size={17} />

                            <input
                              value={lastName}
                              onChange={(event) =>
                                setLastName(
                                  event.target.value,
                                )
                              }
                              autoComplete="family-name"
                              placeholder="Last name"
                              className="w-full bg-transparent text-sm text-[#173d70] outline-none placeholder:text-[#9badc1]"
                            />
                          </Field>
                        </div>
                      )}

                      {(mode === "signin" ||
                        mode === "signup" ||
                        mode === "forgot") && (
                        <div
                          className={
                            mode === "signup"
                              ? "mt-4"
                              : "mt-6"
                          }
                        >
                          <Field
                            label="Email address"
                            error={fieldErrors.email}
                          >
                            <Mail size={17} />

                            <input
                              type="email"
                              value={email}
                              onChange={(event) =>
                                setEmail(
                                  event.target.value,
                                )
                              }
                              autoComplete="email"
                              placeholder="you@example.com"
                              className="w-full bg-transparent text-sm text-[#173d70] outline-none placeholder:text-[#9badc1]"
                            />
                          </Field>
                        </div>
                      )}

                      {(mode === "signin" ||
                        mode === "signup") && (
                        <div className="mt-4">
                          <Field
                            label="Password"
                            error={
                              fieldErrors.password
                            }
                          >
                            <LockKeyhole size={17} />

                            <input
                              type={
                                showPassword
                                  ? "text"
                                  : "password"
                              }
                              value={password}
                              onChange={(event) =>
                                setPassword(
                                  event.target.value,
                                )
                              }
                              autoComplete={
                                mode === "signup"
                                  ? "new-password"
                                  : "current-password"
                              }
                              placeholder={
                                mode === "signup"
                                  ? "Create a secure password"
                                  : "Enter your password"
                              }
                              className="w-full bg-transparent text-sm text-[#173d70] outline-none placeholder:text-[#9badc1]"
                            />

                            <PasswordVisibilityButton
                              visible={showPassword}
                              onClick={() =>
                                setShowPassword(
                                  (value) => !value,
                                )
                              }
                            />
                          </Field>

                          {mode === "signup" &&
                            password.length > 0 && (
                              <PasswordRules
                                checks={
                                  signupPasswordChecks
                                }
                              />
                            )}
                        </div>
                      )}

                      {mode === "reset" && (
                        <div className="mt-6">
                          <Field
                            label="New password"
                            error={
                              fieldErrors.newPassword
                            }
                          >
                            <KeyRound size={17} />

                            <input
                              type={
                                showResetPassword
                                  ? "text"
                                  : "password"
                              }
                              value={resetPassword}
                              onChange={(event) =>
                                setResetPassword(
                                  event.target.value,
                                )
                              }
                              autoComplete="new-password"
                              placeholder="Create your new password"
                              className="w-full bg-transparent text-sm text-[#173d70] outline-none placeholder:text-[#9badc1]"
                            />

                            <PasswordVisibilityButton
                              visible={
                                showResetPassword
                              }
                              onClick={() =>
                                setShowResetPassword(
                                  (value) => !value,
                                )
                              }
                            />
                          </Field>

                          {resetPassword.length > 0 && (
                            <PasswordRules
                              checks={
                                resetPasswordChecks
                              }
                            />
                          )}
                        </div>
                      )}

                      {mode === "signin" && (
                        <div className="mt-3 flex justify-end">
                          <button
                            type="button"
                            onClick={() =>
                              switchMode("forgot")
                            }
                            className="text-sm font-semibold text-[#5275a7] transition hover:text-[#174d98]"
                          >
                            Forgot password?
                          </button>
                        </div>
                      )}

                      {error && (
                        <div
                          role="alert"
                          className="mt-5 rounded-2xl border border-red-200 bg-red-50 px-4 py-3 text-sm leading-6 text-red-700"
                        >
                          {error}
                        </div>
                      )}

                      {message && (
                        <div className="mt-5 rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm leading-6 text-emerald-800">
                          {message}
                        </div>
                      )}

                      <button
                        type="submit"
                        disabled={submitting}
                        className="blue-button mt-6 flex w-full items-center justify-center gap-2 rounded-2xl px-5 py-3.5 text-sm font-bold transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-65"
                      >
                        {submitting && (
                          <LoaderCircle
                            size={17}
                            className="animate-spin"
                          />
                        )}

                        {mode === "signin" &&
                          "Sign In"}

                        {mode === "signup" &&
                          "Create Account"}

                        {mode === "forgot" &&
                          "Send Reset Instructions"}

                        {mode === "reset" &&
                          "Reset Password"}
                      </button>
                    </form>
                  )}
                </div>
              </section>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function passwordRules(value: string) {
  return {
    length: value.length >= 10,
    letter: /[A-Za-z]/.test(value),
    number: /\d/.test(value),
  };
}

function PasswordRules({
  checks,
}: {
  checks: {
    length: boolean;
    letter: boolean;
    number: boolean;
  };
}) {
  return (
    <div className="mt-3 grid gap-2 text-xs text-[#6d819a] sm:grid-cols-3">
      <PasswordCheck
        valid={checks.length}
        label="10+ characters"
      />

      <PasswordCheck
        valid={checks.letter}
        label="One letter"
      />

      <PasswordCheck
        valid={checks.number}
        label="One number"
      />
    </div>
  );
}

function PasswordVisibilityButton({
  visible,
  onClick,
}: {
  visible: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={
        visible ? "Hide password" : "Show password"
      }
      className="text-[#7890ad] transition hover:text-[#173d70]"
    >
      {visible ? (
        <EyeOff size={17} />
      ) : (
        <Eye size={17} />
      )}
    </button>
  );
}

function ModeButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-xl px-4 py-2.5 text-sm font-bold transition ${
        active
          ? "bg-white text-[#173d70] shadow-[0_7px_20px_rgba(45,76,126,.1)]"
          : "text-[#6c82a0] hover:text-[#173d70]"
      }`}
    >
      {children}
    </button>
  );
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="block">
      <span className="text-sm font-bold text-[#28496f]">
        {label}
      </span>

      <div
        className={`mt-2 flex items-center gap-3 rounded-2xl border bg-white px-4 py-3.5 text-[#8296ae] shadow-[0_5px_18px_rgba(36,63,101,.04)] transition focus-within:ring-4 ${
          error
            ? "border-red-300 focus-within:ring-red-100"
            : "border-[#d7e3f1] focus-within:border-[#82aaf1] focus-within:ring-[#4d82e8]/10"
        }`}
      >
        {children}
      </div>

      {error && (
        <span className="mt-1.5 block text-xs font-medium text-red-600">
          {error}
        </span>
      )}
    </label>
  );
}

function PasswordCheck({
  valid,
  label,
}: {
  valid: boolean;
  label: string;
}) {
  return (
    <span
      className={`flex items-center gap-1.5 ${
        valid ? "text-emerald-700" : ""
      }`}
    >
      <span
        className={`grid h-4 w-4 place-items-center rounded-full ${
          valid
            ? "bg-emerald-100"
            : "bg-[#edf2f8]"
        }`}
      >
        <Check size={10} />
      </span>

      {label}
    </span>
  );
}

function Feature({
  icon,
  title,
  description,
}: {
  icon: ReactNode;
  title: string;
  description: string;
}) {
  return (
    <div className="flex gap-3">
      <span className="mt-0.5 grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-white/12 ring-1 ring-white/15">
        {icon}
      </span>

      <div>
        <p className="text-sm font-bold">{title}</p>

        <p className="mt-1 text-xs leading-5 text-white/60">
          {description}
        </p>
      </div>
    </div>
  );
}

function GoogleMark() {
  return (
    <span className="grid h-5 w-5 place-items-center rounded-full border border-[#d8e1ed] bg-white text-[11px] font-black text-[#4285f4]">
      G
    </span>
  );
}
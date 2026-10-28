"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { Building2, Eye, EyeOff, LockKeyhole, ShieldCheck } from "lucide-react";
import { adminApi } from "@/lib/admin-api";

export default function AdminLogin() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(""); setBusy(true);
    try {
      const user = await adminApi.login(email.trim(), password);
      if (user.role !== "ADMIN") throw new Error("Administrator access is required.");
      router.replace("/admin"); router.refresh();
    } catch (err) { setError(err instanceof Error ? err.message : "Unable to sign in."); }
    finally { setBusy(false); }
  }
  return <main className="sa-login-page"><div className="sa-login-frame"><div className="sa-login-intro"><div className="sa-login-logo"><Building2 size={23} /><strong>SpatialStay</strong><span>ADMIN</span></div><div><div className="sa-eyebrow">PLATFORM OPERATIONS</div><h1>Control the platform.<br />Keep every stay accountable.</h1><p>Review hotel onboarding, monitor reservations and payments, and investigate operational exceptions from one secure workspace.</p></div><div className="sa-login-foot"><ShieldCheck size={17} /> Restricted access · Authorized administrators only</div></div><form className="sa-login-form" onSubmit={submit}><div className="sa-eyebrow">SECURE SIGN IN</div><h2>Welcome back</h2><p>Sign in with your SpatialStay administrator credentials.</p>{error && <div className="sa-error" role="alert">{error}</div>}<label>Email address<input type="email" autoComplete="username" required value={email} onChange={(event) => setEmail(event.target.value)} placeholder="admin@example.com" /></label><label>Password<div className="sa-password"><input type={visible ? "text" : "password"} autoComplete="current-password" required value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Enter your password" /><button type="button" aria-label={visible ? "Hide password" : "Show password"} onClick={() => setVisible(!visible)}>{visible ? <EyeOff size={19} /> : <Eye size={19} />}</button></div></label><button className="sa-button sa-button-primary sa-login-submit" disabled={busy}><LockKeyhole size={17} /> {busy ? "Signing in…" : "Sign in to Admin"}</button><div className="sa-login-note">Admin accounts are provisioned by trusted platform operators. Public registration is not available.</div></form></div></main>;
}

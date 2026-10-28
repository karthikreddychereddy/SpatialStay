import type { Metadata } from "next";
import { SiteChrome } from "../components/layout/SiteChrome";
import "./globals.css";

export const metadata: Metadata = {
  title: "SpatialStay — AI Hotel Discovery",
  description: "AI-first spatial hotel discovery and booking experience",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
        <SiteChrome />
        {children}
      </body>
    </html>
  );
}
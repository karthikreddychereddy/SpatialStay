"use client";

import { usePathname } from "next/navigation";

import { AIFloatingButton } from "@/components/ai/AIFloatingButton";
import { Navbar } from "@/components/layout/Navbar";

export function SiteChrome() {
  const pathname = usePathname();

  const isAdminRoute =
    pathname === "/admin" ||
    pathname.startsWith("/admin/");

  const isHotelOperationsRoute =
    pathname === "/hotel" ||
    pathname.startsWith("/hotel/");

  const isHotelPartnerApplicationRoute =
    pathname === "/hotel-partner/apply" ||
    pathname.startsWith("/hotel-partner/apply/");

  const hidePublicChrome =
    isAdminRoute ||
    isHotelOperationsRoute ||
    isHotelPartnerApplicationRoute;

  if (hidePublicChrome) {
    return null;
  }

  return (
    <>
      <Navbar />
      <AIFloatingButton />
    </>
  );
} 
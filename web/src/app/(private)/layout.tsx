import type { Metadata } from "next";
import type { ReactNode } from "react";
import { requireUser } from "@/features/auth/current-user";

export const metadata: Metadata = { robots: { index: false, follow: false } };

export default async function PrivateLayout({ children }: { children: ReactNode }) {
  await requireUser();
  return <>{children}</>;
}

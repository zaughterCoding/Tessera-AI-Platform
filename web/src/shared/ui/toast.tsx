"use client";

import { clsx } from "clsx";
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";

type ToastTone = "info" | "success" | "error";
type ToastState = { id: number; text: string; tone: ToastTone };
type Notify = (text: string, tone?: ToastTone) => void;

const ToastContext = createContext<Notify | null>(null);
const TOAST_MS = 4200;

const TONES: Record<ToastTone, string> = {
  info: "bg-ink text-white",
  success: "bg-success text-white",
  error: "bg-danger text-white",
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState | null>(null);
  const nextId = useRef(0);

  const notify = useCallback<Notify>((text, tone = "info") => {
    nextId.current += 1;
    setToast({ id: nextId.current, text, tone });
  }, []);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), TOAST_MS);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const value = useMemo(() => notify, [notify]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-4 bottom-4 z-50 flex justify-center">
        {toast && (
          <div
            key={toast.id}
            role={toast.tone === "error" ? "alert" : "status"}
            className={clsx("pointer-events-auto max-w-md rounded-md px-4 py-2.5 text-sm shadow-overlay", TONES[toast.tone])}
          >
            {toast.text}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): Notify {
  const notify = useContext(ToastContext);
  if (!notify) throw new Error("useToast 必须在 ToastProvider 内使用");
  return notify;
}

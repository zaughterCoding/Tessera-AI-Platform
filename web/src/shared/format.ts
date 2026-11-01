const ZONE = "Asia/Shanghai";

// 服务端和浏览器都固定业务时区，避免水合时文本不一致。
const timeFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: ZONE,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

// 后端时间不带时区后缀，约定为 UTC。
export function parseServerTime(value: string | null | undefined): Date | null {
  if (!value) return null;
  const zoned = /([zZ]|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`;
  const date = new Date(zoned);
  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatTime(value: string | null | undefined): string {
  const date = parseServerTime(value);
  return date ? timeFormatter.format(date) : "—";
}

export function formatDate(value: string | null | undefined): string {
  const date = parseServerTime(value);
  return date ? dateFormatter.format(date) : "—";
}

// 金额是字符串形式的整数分。
export function formatMoney(cents: string | number | null | undefined): string {
  if (cents == null || cents === "") return "—";
  const amount = Number(cents);
  if (!Number.isFinite(amount)) return "—";
  if (amount === 0) return "免费";
  return `¥${(amount / 100).toFixed(2)}`;
}

export function formatScore(score: number | null | undefined): string {
  return score == null ? "—" : (score / 10).toFixed(1);
}

export function formatCountdown(remainingMs: number): string {
  const total = Math.max(0, Math.floor(remainingMs / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const pad = (value: number) => String(value).padStart(2, "0");
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
}

export function formatBytes(bytes: string | number | null | undefined): string {
  const value = Number(bytes);
  if (!Number.isFinite(value)) return "—";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

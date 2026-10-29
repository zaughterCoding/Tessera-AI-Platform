// 服务端时间按 UTC 约定输出且不带时区后缀；统一补上 Z 再格式化。
export function parseServerTime(value) {
  if (value == null || value === "") return null;
  if (value instanceof Date) return value;
  if (typeof value === "number" || /^\d{13}$/.test(String(value))) {
    const epoch = new Date(Number(value));
    return Number.isNaN(epoch.getTime()) ? null : epoch;
  }
  const text = String(value);
  const zoned = /([zZ]|[+-]\d{2}:\d{2})$/.test(text) ? text : text + "Z";
  const date = new Date(zoned);
  return Number.isNaN(date.getTime()) ? null : date;
}

const timeFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hour12: false,
});

const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
  timeZone: "Asia/Shanghai",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

export function formatTime(value) {
  const date = parseServerTime(value);
  if (!date) return "—";
  return timeFormatter.format(date) + " 北京时间";
}

export function formatDate(value) {
  const date = parseServerTime(value);
  if (!date) return "—";
  return dateFormatter.format(date);
}

// 金额为整数分，服务端以字符串返回，展示时再转换为两位小数。
export function money(cents) {
  if (cents == null || cents === "") return "—";
  const amount = Number(cents);
  if (!Number.isFinite(amount)) return "—";
  if (amount === 0) return "免费";
  return "¥" + (amount / 100).toFixed(2);
}

export function countdown(expireAt, now = Date.now()) {
  const target = parseServerTime(expireAt);
  if (!target) return "";
  const remaining = Math.max(0, target.getTime() - now);
  const totalSeconds = Math.floor(remaining / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return String(minutes).padStart(2, "0") + ":" + String(seconds).padStart(2, "0");
}

export function isExpired(expireAt, now = Date.now()) {
  const target = parseServerTime(expireAt);
  return !target || target.getTime() <= now;
}

export function truncate(value, size) {
  if (!value) return "";
  return value.length > size ? value.slice(0, size) + "…" : value;
}

export const ORDER_STATUS_LABELS = {
  UNPAID: "待支付",
  PAID: "已支付",
  CANCELLED: "已关闭",
};

export const RESERVATION_LABELS = {
  PROCESSING: "受理中",
  BOOKED: "已建单",
  RELEASED: "已关闭",
  SUCCESS: "建单成功",
  REVIEW: "待人工复核",
  FAILED: "未成功",
};

export function orderStatusLabel(status) {
  return ORDER_STATUS_LABELS[status] || status || "未知";
}

export function reservationLabel(state) {
  return RESERVATION_LABELS[state] || state || "未知";
}

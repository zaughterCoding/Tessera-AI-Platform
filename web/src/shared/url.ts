export type SearchParams = Record<string, string | string[] | undefined>;

export function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

export function parsePage(value: string | string[] | undefined): number {
  const page = Number(firstParam(value));
  return Number.isInteger(page) && page >= 1 ? page : 1;
}

export function parseId(value: string | string[] | undefined): string | undefined {
  const id = firstParam(value);
  return id && /^[1-9]\d{0,18}$/.test(id) ? id : undefined;
}

// 基于当前筛选条件生成新链接；值为空的条件从 URL 中去掉。
export function hrefWith(base: string, current: Record<string, string | number | undefined>, changes: Record<string, string | number | undefined> = {}): string {
  const query = new URLSearchParams();
  Object.entries({ ...current, ...changes }).forEach(([key, value]) => {
    if (value != null && value !== "") query.set(key, String(value));
  });
  const text = query.toString();
  return text ? `${base}?${text}` : base;
}

export function totalPagesOf(total: number | null, size: number): number {
  return Math.max(1, Math.ceil((total ?? 0) / size));
}

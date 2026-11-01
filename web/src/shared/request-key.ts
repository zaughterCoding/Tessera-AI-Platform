const PREFIX = "aiplatform.request-key.";

function createKey(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
}

// 同一业务请求重试必须复用同一个键，后端才能返回原结果而不是重复执行。
export function getRequestKey(scope: string): string {
  const storageKey = PREFIX + scope;
  const existing = sessionStorage.getItem(storageKey);
  if (existing) return existing;
  const key = createKey();
  sessionStorage.setItem(storageKey, key);
  return key;
}

export function peekRequestKey(scope: string): string | null {
  return sessionStorage.getItem(PREFIX + scope);
}

export function rememberRequestKey(scope: string, key: string) {
  sessionStorage.setItem(PREFIX + scope, key);
}

export function clearRequestKey(scope: string) {
  sessionStorage.removeItem(PREFIX + scope);
}

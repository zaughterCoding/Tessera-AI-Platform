const TOKEN_KEY = "aiplatform.token";
const USER_KEY = "aiplatform.user";

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || "";
  } catch {
    return "";
  }
}

export function getUser() {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function saveSession(token, user) {
  try {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user || null));
  } catch {
    // 本地存储不可用时仍允许本次会话在内存中生效。
  }
}

export function saveUser(user) {
  try {
    localStorage.setItem(USER_KEY, JSON.stringify(user || null));
  } catch {
    // 忽略存储失败，调用方继续使用内存身份。
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  } catch {
    // 忽略存储失败。
  }
}

export function isAdmin(user) {
  return Boolean(user && Array.isArray(user.roles) && user.roles.indexOf("ADMIN") >= 0);
}

// 会话失效只通知界面，避免公共页面因过期令牌被强制跳走。
export function notifySessionExpired() {
  window.dispatchEvent(new CustomEvent("aiplatform:session-expired"));
}

export function requestKey(scope) {
  const storageKey = "aiplatform.request-key." + scope;
  let key = null;
  try {
    key = sessionStorage.getItem(storageKey);
  } catch {
    key = null;
  }
  if (!key) {
    key = createKey();
    try {
      sessionStorage.setItem(storageKey, key);
    } catch {
      // 内存键在本次页面生命周期内仍然有效。
    }
  }
  return key;
}

export function clearRequestKey(scope) {
  try {
    sessionStorage.removeItem("aiplatform.request-key." + scope);
  } catch {
    // 忽略存储失败。
  }
}

// 服务端可能返回规范化后的请求键，后续重试必须继续复用它。
export function rememberRequestKey(scope, key) {
  if (!key) return;
  try {
    sessionStorage.setItem("aiplatform.request-key." + scope, key);
  } catch {
    // 忽略存储失败。
  }
}

function createKey() {
  const random = new Uint8Array(16);
  if (window.crypto && window.crypto.getRandomValues) {
    window.crypto.getRandomValues(random);
  } else {
    for (let index = 0; index < random.length; index++) {
      random[index] = Math.floor(Math.random() * 256);
    }
  }
  return Array.from(random, (value) => value.toString(16).padStart(2, "0")).join("");
}

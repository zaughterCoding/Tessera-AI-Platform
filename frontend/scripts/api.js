export class ApiError extends Error {
  constructor(message, status = 0, code = "NETWORK_ERROR", requestId = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.requestId = requestId;
  }
}

// 兼容尚未转换的长整型响应，避免解析时丢失标识精度。
export function parseResponseText(text) {
  const lossless = text.replace(
    /"(?:\\.|[^"\\])*"|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/g,
    (token) =>
      /^-?\d{16,}$/.test(token) && !Number.isSafeInteger(Number(token))
        ? JSON.stringify(token)
        : token,
  );
  return JSON.parse(lossless);
}

export function createApi({
  fetchImpl = fetch,
  tokenProvider = () => null,
  onUnauthorized = () => {},
  timeout = 10000,
}) {
  return async function request(path, { method = "GET", params, body, quiet = false } = {}) {
    const query = new URLSearchParams();
    Object.entries(params || {}).forEach(([key, value]) => {
      if (value != null && value !== "") query.set(key, String(value));
    });
    const url = "/api" + path + (query.size ? "?" + query.toString() : "");
    const headers = { Accept: "application/json" };
    const token = tokenProvider();
    if (token) headers.authorization = token;
    const multipart = typeof FormData !== "undefined" && body instanceof FormData;
    if (body != null && !multipart) headers["Content-Type"] = "application/json";
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      const response = await fetchImpl(url, {
        method,
        headers,
        credentials: "same-origin",
        signal: controller.signal,
        body: body == null ? undefined : multipart ? body : JSON.stringify(body),
      });
      let result;
      try {
        result = parseResponseText(await response.text());
      } catch {
        if (response.ok) throw new ApiError("响应格式不正确", response.status, "INVALID_RESPONSE");
      }
      if (response.status === 401) onUnauthorized(quiet);
      if (!response.ok || !result?.success) {
        throw new ApiError(
          result?.errorMsg || "操作未完成，请稍后重试",
          response.status,
          result?.code || "HTTP_ERROR",
          result?.requestId || response.headers?.get("X-Request-ID") || null,
        );
      }
      return result;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(
        error.name === "AbortError" ? "请求超时，请稍后重试" : "网络连接失败，请检查连接",
        0,
        error.name === "AbortError" ? "TIMEOUT" : "NETWORK_ERROR",
      );
    } finally {
      clearTimeout(timer);
    }
  };
}

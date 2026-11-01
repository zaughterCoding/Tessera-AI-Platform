const ALLOWED_TYPES = ["image/png", "image/jpeg", "image/webp"];
const MAX_BYTES = 5_000_000;

export function validateMediaFile(file: File): string | null {
  if (!ALLOWED_TYPES.includes(file.type)) return "只支持 PNG、JPEG、WebP 图片。";
  if (file.size > MAX_BYTES) return "图片不能超过 5 MB。";
  return null;
}

function uploadErrorMessage(status: number, code: string | undefined, message: string | undefined): string {
  if (code === "QUOTA_EXCEEDED") return "素材配额已用完，请先删除不用的素材。";
  if (status === 401) return "登录已过期，请重新登录。";
  return message || "上传失败，请重试。";
}

type UploadBody = { success?: boolean; code?: string; errorMsg?: string };

function parseBody(text: string): UploadBody {
  try {
    return JSON.parse(text) as UploadBody;
  } catch {
    return {};
  }
}

// fetch 拿不到上传进度，这里用 XMLHttpRequest。
export function uploadMedia(file: File, onProgress: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const form = new FormData();
    form.append("file", file);
    const request = new XMLHttpRequest();
    request.open("POST", "/api/media");
    request.timeout = 60_000;
    request.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress(Math.round((event.loaded / event.total) * 100));
    };
    request.onload = () => {
      const body = parseBody(request.responseText);
      if (request.status >= 200 && request.status < 300 && body.success) resolve();
      else reject(new Error(uploadErrorMessage(request.status, body.code, body.errorMsg)));
    };
    request.onerror = () => reject(new Error("网络连接失败，请重试。"));
    request.ontimeout = () => reject(new Error("上传超时，请重试。"));
    request.send(form);
  });
}

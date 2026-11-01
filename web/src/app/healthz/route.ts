export const dynamic = "force-dynamic";

// 只反映前端进程存活，不探测后端，避免后端抖动时入口被判定不健康。
export function GET() {
  return new Response("ok\n", { headers: { "content-type": "text/plain" } });
}

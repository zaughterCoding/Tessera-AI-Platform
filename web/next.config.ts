import { dirname } from "node:path";
import { fileURLToPath } from "node:url";
import type { NextConfig } from "next";

const projectRoot = dirname(fileURLToPath(import.meta.url));

const nextConfig: NextConfig = {
  output: "standalone",
  outputFileTracingRoot: projectRoot,
  turbopack: { root: projectRoot },
  poweredByHeader: false,
  reactStrictMode: true,
  // 元数据始终写进 head，分享预览和无 JS 访问都能拿到。
  htmlLimitedBots: /.*/,
  // 取消发布后媒体必须立即失效，不能让优化缓存保留可访问副本。
  images: { unoptimized: true },
};

export default nextConfig;

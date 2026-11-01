import { z } from "zod";

function isBareOrigin(value: string): boolean {
  const url = new URL(value);
  return url.pathname === "/" && !url.search && !url.hash && !url.username && !url.password;
}

const serverEnvSchema = z.object({
  // pipe 保证只有合法 URL 才进入来源校验。
  API_ORIGIN: z
    .url({ protocol: /^https?$/ })
    .pipe(z.string().refine(isBareOrigin, "API_ORIGIN 只能包含协议、主机和端口"))
    .transform((value) => value.replace(/\/$/, "")),
});

export type ServerEnv = z.infer<typeof serverEnvSchema>;

// 配置错误在启动时暴露，避免首个请求才失败。
export function parseServerEnv(source: Record<string, string | undefined>): ServerEnv {
  const result = serverEnvSchema.safeParse(source);
  if (!result.success) {
    const fields = result.error.issues.map((issue) => issue.path.join(".")).join(", ");
    throw new Error(`服务端环境变量无效：${fields}`);
  }
  return result.data;
}

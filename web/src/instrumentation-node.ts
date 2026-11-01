import { parseServerEnv } from "@/shared/config/server-env";

// Next 捕获启动钩子异常后进程不会退出，必须主动结束，容器才能感知配置错误。
export function assertServerEnv() {
  try {
    parseServerEnv(process.env);
  } catch (error) {
    console.error(error instanceof Error ? error.message : "服务端环境变量无效");
    process.exit(1);
  }
}

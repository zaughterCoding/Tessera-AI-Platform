import { z } from "zod";

// 后端 Long 一律输出为字符串，全程保持字符串，避免 JS 数字丢失精度。
export const longString = z.string().regex(/^-?\d+$/);
export type LongString = z.infer<typeof longString>;

export const roleSchema = z.string();

export const userSchema = z.object({
  id: longString,
  nickName: z.string(),
  icon: z.string().nullish(),
  roles: z.array(roleSchema).default([]),
});
export type User = z.infer<typeof userSchema>;

export function isAdmin(user: User | null | undefined): boolean {
  return Boolean(user?.roles.includes("ADMIN"));
}

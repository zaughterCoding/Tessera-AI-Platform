import { z } from "zod";
import { longString } from "@/shared/api/types";

export const creationSchema = z.object({
  id: longString,
  userId: longString,
  authorNickName: z.string().nullish(),
  authorIcon: z.string().nullish(),
  applicationId: longString.nullish(),
  applicationName: z.string().nullish(),
  kind: z.string(),
  title: z.string(),
  content: z.string().nullish(),
  state: z.string(),
  likeCount: z.number(),
  liked: z.boolean().nullish(),
  publishedAt: z.string().nullish(),
  createdAt: z.string().nullish(),
  media: z
    .array(z.object({ mediaId: longString, position: z.number().nullish(), url: z.string().nullish() }))
    .default([]),
});
export type Creation = z.infer<typeof creationSchema>;

export const likeResultSchema = z.object({ liked: z.boolean(), likeCount: z.number() });
export type LikeResult = z.infer<typeof likeResultSchema>;

export const followResultSchema = z.object({
  following: z.boolean(),
  followerCount: z.union([longString, z.number()]).nullish(),
  followingCount: z.union([longString, z.number()]).nullish(),
});
export type FollowResult = z.infer<typeof followResultSchema>;

export const followUserSchema = z.object({
  userId: longString,
  nickName: z.string().nullish(),
  icon: z.string().nullish(),
});

export const feedResultSchema = z.object({
  records: z.array(creationSchema),
  nextTime: longString.nullish(),
  nextOffset: z.number().nullish(),
  hasMore: z.boolean(),
});

export function kindLabel(kind: string): string {
  return kind === "REVIEW" ? "应用评测" : "作品";
}

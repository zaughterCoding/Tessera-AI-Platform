"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { goToLogin, useSession } from "@/features/auth/session-provider";
import { api, ApiError } from "@/shared/api/client";
import { Button } from "@/shared/ui/button";
import { useToast } from "@/shared/ui/toast";
import { followResultSchema, type FollowResult } from "./schemas";

export function FollowButton({ userId }: { userId: string }) {
  const { user } = useSession();
  const toast = useToast();
  const queryClient = useQueryClient();
  const queryKey = ["follow", userId];
  const isSelf = user?.id === userId;

  const status = useQuery({
    queryKey,
    enabled: Boolean(user) && !isSelf,
    queryFn: async () => (await api(`/follows/${userId}/status`, followResultSchema)).data,
  });

  const follow = useMutation({
    mutationFn: async (following: boolean) =>
      (await api(`/follows/${userId}`, followResultSchema, { method: following ? "PUT" : "DELETE" })).data,
    onMutate: async (following) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<FollowResult>(queryKey);
      queryClient.setQueryData<FollowResult>(queryKey, (current) => ({ ...current, following }));
      return { previous };
    },
    onError: (error, _following, context) => {
      queryClient.setQueryData(queryKey, context?.previous);
      if (!(error instanceof ApiError && error.status === 401)) toast("操作没有成功，已恢复原状态", "error");
    },
    onSuccess: (data) => queryClient.setQueryData(queryKey, data),
  });

  if (isSelf) return null;
  if (!user) return <Button size="sm" variant="secondary" onClick={goToLogin}>关注</Button>;
  const following = status.data?.following ?? false;
  return (
    <Button
      size="sm"
      variant={following ? "secondary" : "primary"}
      aria-pressed={following}
      disabled={status.isPending}
      onClick={() => follow.mutate(!following)}
    >
      {following ? "已关注" : "关注"}
    </Button>
  );
}

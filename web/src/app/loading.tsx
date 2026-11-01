import { Skeleton } from "@/shared/ui/feedback";

export default function Loading() {
  return (
    <div role="status" aria-label="正在加载" className="flex flex-col gap-4">
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-40 w-full" />
      <Skeleton className="h-40 w-full" />
    </div>
  );
}

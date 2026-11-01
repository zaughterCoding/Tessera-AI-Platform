import Link from "@/shared/ui/link";
import { AppGrid } from "@/features/catalog/app-card";
import { fetchApplications, fetchCategories, fetchPopularity } from "@/features/catalog/server";
import { LinkButton } from "@/shared/ui/button";

const FLOW = [
  { title: "选择使用包", text: "按规格购买，到账后才能使用" },
  { title: "提交生成任务", text: "次数先冻结，刷新也不会丢" },
  { title: "拿到私有结果", text: "全部交付后才扣次数" },
  { title: "主动分享作品", text: "默认仅自己可见，公开由你决定" },
];

export default async function HomePage() {
  const [categories, { data: newest }, popular] = await Promise.all([
    fetchCategories(),
    fetchApplications({ sort: "newest", size: 6 }),
    fetchPopularity(6),
  ]);

  return (
    <div className="flex flex-col gap-10">
      <section className="grid gap-8 py-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-center lg:py-8">
        <div>
          <h1 className="text-3xl leading-tight font-semibold tracking-tight sm:text-4xl">
            挑一个 AI 应用，
            <br />
            把灵感变成作品。
          </h1>
          <p className="mt-4 max-w-md text-ink-soft">购买使用包、提交生成任务、下载私有结果，再把满意的作品分享到社区。</p>
          <div className="mt-6 flex flex-wrap gap-3">
            <LinkButton href="/apps">浏览应用</LinkButton>
            <LinkButton href="/community" variant="secondary">看看作品</LinkButton>
          </div>
        </div>
        <ol className="grid gap-px overflow-hidden rounded-lg border border-line bg-line sm:grid-cols-2">
          {FLOW.map((step, index) => (
            <li key={step.title} className="bg-surface p-4">
              <span className="text-xs font-medium text-accent">0{index + 1}</span>
              <p className="mt-1 font-medium">{step.title}</p>
              <p className="mt-0.5 text-sm text-ink-muted">{step.text}</p>
            </li>
          ))}
        </ol>
      </section>

      <nav aria-label="应用分类" className="flex flex-wrap gap-2">
        {categories.map((category) => (
          <Link
            key={category.id}
            href={`/apps?categoryId=${category.id}`}
            className="rounded-full border border-line-strong px-3 py-1 text-sm text-ink-soft hover:text-ink"
          >
            {category.name}
          </Link>
        ))}
      </nav>

      <section aria-labelledby="newest-heading">
        <div className="mb-4 flex items-baseline justify-between">
          <h2 id="newest-heading" className="text-lg font-semibold">最近上架</h2>
          <Link href="/apps?sort=newest" className="text-sm text-ink-muted hover:text-ink">查看全部</Link>
        </div>
        <AppGrid applications={newest} priorityCount={3} />
      </section>

      {popular.length > 0 && (
        <section aria-labelledby="popular-heading">
          <h2 id="popular-heading" className="mb-4 text-lg font-semibold">近一小时热榜</h2>
          <ol className="divide-y divide-line overflow-hidden rounded-lg border border-line bg-surface">
            {popular.map((item) => (
              <li key={item.applicationId}>
                <Link href={`/apps/${item.applicationId}`} className="flex items-center gap-3 px-4 py-3 hover:bg-sunken">
                  <span className="w-5 text-sm font-semibold text-accent">{item.rank}</span>
                  <span className="min-w-0 flex-1 truncate text-sm">
                    <span className="font-medium">{item.name}</span>
                    {item.summary && <span className="ml-2 text-ink-muted">{item.summary}</span>}
                  </span>
                </Link>
              </li>
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}

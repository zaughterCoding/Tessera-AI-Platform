import { clsx } from "clsx";
import Image from "next/image";

const PALETTES = [
  "from-orange-200 to-rose-300",
  "from-amber-200 to-orange-300",
  "from-teal-200 to-cyan-300",
  "from-indigo-200 to-sky-300",
  "from-lime-200 to-emerald-300",
  "from-fuchsia-200 to-pink-300",
];

function paletteOf(seed: string): string {
  let hash = 0;
  for (const char of seed) hash = (hash * 31 + (char.codePointAt(0) ?? 0)) >>> 0;
  return PALETTES[hash % PALETTES.length] ?? "from-orange-200 to-rose-300";
}

// 容器固定比例，图片加载前后页面都不跳动；没有封面时用名称生成占位。
export function AppCover({
  name,
  coverUrl,
  sizes,
  priority = false,
  className,
}: {
  name: string;
  coverUrl?: string | null | undefined;
  sizes: string;
  priority?: boolean;
  className?: string;
}) {
  return (
    <div className={clsx("relative aspect-[16/10] overflow-hidden bg-sunken", className)}>
      {coverUrl ? (
        <Image src={coverUrl} alt={`${name} 封面`} fill sizes={sizes} priority={priority} className="object-cover" />
      ) : (
        <div aria-hidden className={clsx("flex size-full items-center justify-center bg-gradient-to-br", paletteOf(name))}>
          <span className="text-4xl font-semibold text-ink/40">{name.slice(0, 2)}</span>
        </div>
      )}
    </div>
  );
}

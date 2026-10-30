// 从锁定的 npm 包复制 Vue 3 浏览器全局构建（含运行时模板编译器）到 vendor。
// 该脚本只在升级前端依赖时使用；产品构建与运行直接使用已提交的 vendor/vue.js。
import { copyFileSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const packageRoot = join(root, "node_modules", "vue");
const source = join(packageRoot, "dist", "vue.global.prod.js");
const target = join(root, "vendor", "vue.js");

const { version } = JSON.parse(readFileSync(join(packageRoot, "package.json"), "utf8"));
copyFileSync(source, target);
console.log(`vendor/vue.js <- vue@${version}`);

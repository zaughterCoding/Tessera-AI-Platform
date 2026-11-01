// 演示数据：全部通过真实接口写入，走完购买、到账、生成、交付和分享的正常流程。
// 用法：node seed.mjs admin-login | node seed.mjs seed
const BASE = process.env.SEED_BASE_URL ?? "http://web:8080";
const ADMIN_PHONE = "13800000000";
const CREATOR_PRODUCT = "102";
const LIMITED_PRODUCTS = ["120", "118"];

const CREATORS = [
  {
    phone: "13800000001",
    name: "星野",
    works: [
      ["雨后的霓虹街道", "潮湿的路面倒映着霓虹招牌，低饱和，电影感。"],
      ["月光下的玻璃温室", "夜晚的温室里长满发光植物，安静而梦幻。"],
      ["漂浮的书店", "悬在云层里的旧书店，暖色灯光从窗口透出。"],
    ],
  },
  {
    phone: "13800000002",
    name: "阿澈",
    works: [
      ["海边的灯塔", "清晨薄雾中的灯塔，水彩风格，留白很多。"],
      ["沙漠列车", "一列蒸汽火车穿过粉色沙丘，超现实。"],
    ],
  },
  {
    phone: "13800000003",
    name: "林间",
    works: [
      ["森林里的小邮局", "苔藓覆盖的木屋，门口挂着手写信箱。"],
      ["极光下的鲸", "巨大的鲸在极光中游过雪原。"],
      ["午后的猫", "窗台上打盹的橘猫，胶片质感。"],
    ],
  },
];

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

class Client {
  cookie = "";

  async request(method, path, body) {
    const response = await fetch(`${BASE}/api${path}`, {
      method,
      headers: {
        Accept: "application/json",
        ...(this.cookie ? { Cookie: this.cookie } : {}),
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
    const setCookie = response.headers.getSetCookie()[0];
    if (setCookie) this.cookie = setCookie.split(";")[0];
    const text = await response.text();
    const parsed = text ? JSON.parse(text) : {};
    return { status: response.status, ...parsed };
  }

  // 认证入口有限流，遇到 429 等待后重试。
  async call(method, path, body) {
    for (let attempt = 0; attempt < 20; attempt++) {
      const result = await this.request(method, path, body);
      if (result.status !== 429) {
        if (!result.success) throw new Error(`${method} ${path}: ${result.status} ${result.code} ${result.errorMsg}`);
        return result.data;
      }
      await sleep(5000);
    }
    throw new Error(`${method} ${path}: 持续被限流`);
  }

  async login(phone) {
    let code;
    for (let attempt = 0; attempt < 30 && !code; attempt++) {
      const result = await this.request("POST", `/user/code?phone=${phone}`);
      if (result.success) code = result.data.demoCode;
      else if (result.status === 429) await sleep(5000);
      else throw new Error(`获取验证码失败：${result.errorMsg}`);
    }
    if (!code) throw new Error("验证码接口持续被限流");
    await this.call("POST", "/auth/login", { phone, code });
    return this.call("GET", "/user/me");
  }
}

function key(label) {
  return `seed-${label}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

async function waitFor(check, label) {
  for (let attempt = 0; attempt < 120; attempt++) {
    const value = await check();
    if (value) return value;
    await sleep(1000);
  }
  throw new Error(`等待超时：${label}`);
}

async function publishWorks(creator) {
  const client = new Client();
  const user = await client.login(creator.phone);
  await client.call("PUT", "/user/me", { nickName: creator.name });

  const order = await client.call("POST", "/trade/orders", { productId: CREATOR_PRODUCT, requestKey: key("order") });
  await client.call("POST", `/trade/orders/${order.id}/pay`, { expectedAmount: order.amount });
  await waitFor(async () => Number((await client.call("GET", "/entitlement/balance")).availableCredits) > 0, "权益到账");

  const grants = await client.call("GET", "/entitlement/grants?page=1&size=1");
  const grant = grants[0];
  const created = [];
  for (const [title, prompt] of creator.works) {
    const task = await client.call("POST", "/generation/tasks", {
      requestKey: key("task"),
      applicationId: grant.applicationId,
      applicationVersion: grant.applicationVersion,
      modelVersion: grant.modelVersion,
      imageSize: grant.imageSize,
      outputCount: grant.outputCount,
      prompt,
      negativePrompt: "",
      mediaIds: [],
    });
    const done = await waitFor(async () => {
      const current = await client.call("GET", `/generation/tasks/${task.id}`);
      return current.state === "SUCCEEDED" ? current : null;
    }, title);
    const creation = await client.call("POST", `/generation/tasks/${task.id}/share`, {
      resultIds: done.results.slice(0, 2).map((result) => result.id),
      title,
      content: prompt,
      published: true,
    });
    created.push(creation.id);
    console.log(`作品：${creator.name} / ${title}`);
  }
  return { client, user, created };
}

function apiTime(date) {
  return date.toISOString().slice(0, 19);
}

async function seedCatalogAndCampaigns(admin) {
  for (let id = 1; id <= 10; id++) {
    await admin.call("PUT", `/admin/catalog/applications/${id}`, { coverUrl: `/covers/${id}.svg` });
  }
  console.log("封面：已设置");

  const existing = await admin.call("GET", "/admin/campaigns");
  if (existing.length) {
    console.log("活动：已存在，跳过");
    return;
  }
  const now = Date.now();
  const windows = [
    [now - 3_600_000, now + 7 * 86_400_000, 50],
    [now + 86_400_000, now + 8 * 86_400_000, 20],
  ];
  for (const [index, productId] of LIMITED_PRODUCTS.entries()) {
    await admin.call("PUT", `/admin/catalog/products/${productId}`, { saleMode: 1 });
    const [beginAt, endAt, initialStock] = windows[index];
    const campaign = await admin.call("POST", "/admin/campaigns", {
      productId,
      initialStock,
      beginAt: apiTime(new Date(beginAt)),
      endAt: apiTime(new Date(endAt)),
    });
    await admin.call("POST", `/admin/campaigns/${campaign.id}/publish`);
    console.log(`活动：${campaign.id}`);
  }
}

async function seed() {
  const admin = new Client();
  const me = await admin.login(ADMIN_PHONE);
  if (!me.roles.includes("ADMIN")) throw new Error("演示管理员尚未授权");
  await admin.call("PUT", "/user/me", { nickName: "演示管理员" });
  await seedCatalogAndCampaigns(admin);

  const creations = await admin.request("GET", "/creations?limit=1&offset=0");
  if (Number(creations.total) > 0) {
    console.log("作品：已存在，跳过");
    return;
  }
  const results = [];
  for (const creator of CREATORS) results.push(await publishWorks(creator));

  // 互相点赞和关注，让热榜和动态有内容。
  for (const { client, user } of results) {
    for (const other of results) {
      if (other.user.id === user.id) continue;
      await client.call("PUT", `/follows/${other.user.id}`);
      for (const id of other.created.slice(0, 2)) await client.call("PUT", `/creations/${id}/likes/me`);
    }
  }
  console.log("互动：已写入");
}

const command = process.argv[2];
if (command === "admin-login") {
  const admin = new Client();
  console.log((await admin.login(ADMIN_PHONE)).id);
} else if (command === "seed") {
  await seed();
  console.log("演示数据准备完成");
} else {
  throw new Error("用法：seed.mjs admin-login | seed");
}

import { registerComponents } from "./components.js";
import { createApi } from "./api.js";
import {
  clearRequestKey,
  clearSession,
  getToken,
  getUser,
  isAdmin,
  rememberRequestKey,
  requestKey,
  saveSession,
  saveUser,
} from "./auth.js";
import {
  countdown,
  formatDate,
  formatTime,
  isExpired,
  orderStatusLabel,
  parseServerTime,
  reservationLabel,
} from "./format.js";
import {
  AppDetailPage,
  AppsPage,
  CampaignDetailPage,
  CampaignsPage,
  HomePage,
  OrderDetailPage,
  OrdersPage,
} from "./pages.js";
import {
  CommunityPage,
  CreatePage,
  CreationDetailPage,
  LoginPage,
  MediaPage,
  MinePage,
  NotFoundPage,
} from "./pages-more.js";
import {
  EntitlementDetailPage,
  EntitlementsPage,
  TaskDetailPage,
  TasksPage,
} from "./pages-generation.js";
import { AdminPage } from "./admin.js";

const { createApp, reactive } = window.Vue;

const ROUTE_TITLES = {
  home: "首页",
  apps: "AI 应用",
  campaigns: "限量活动",
  orders: "我的订单",
  tasks: "我的任务",
  entitlements: "我的权益",
  community: "作品社区",
  creations: "作品详情",
  create: "发布作品",
  media: "我的素材",
  mine: "个人中心",
  login: "登录",
  admin: "运营控制台",
};

function parseRoute() {
  const raw = window.location.hash || "#/home";
  const body = raw.replace(/^#/, "");
  const split = body.indexOf("?");
  const pathPart = (split >= 0 ? body.slice(0, split) : body).replace(/^\/+|\/+$/g, "");
  const queryPart = split >= 0 ? body.slice(split + 1) : "";
  const segments = pathPart
    ? pathPart.split("/").map((part) => {
      try {
        return decodeURIComponent(part);
      } catch {
        return part;
      }
    })
    : [];
  const query = {};
  new URLSearchParams(queryPart).forEach((value, key) => {
    if (!(key in query)) query[key] = value;
  });
  return {
    segments,
    path: "/" + segments.join("/"),
    id: segments.length > 1 ? segments[1] : "",
    query,
    fullPath: raw,
  };
}

function resolvePage(route) {
  const first = route.segments[0] || "home";
  const second = route.segments[1];
  switch (first) {
    case "home":
      return HomePage;
    case "apps":
      return second ? AppDetailPage : AppsPage;
    case "campaigns":
      return second ? CampaignDetailPage : CampaignsPage;
    case "orders":
      return second ? OrderDetailPage : OrdersPage;
    case "tasks":
      return second ? TaskDetailPage : TasksPage;
    case "entitlements":
      return second ? EntitlementDetailPage : EntitlementsPage;
    case "community":
      return CommunityPage;
    case "creations":
      return second ? CreationDetailPage : NotFoundPage;
    case "create":
      return CreatePage;
    case "media":
      return MediaPage;
    case "mine":
      return MinePage;
    case "login":
      return LoginPage;
    case "admin":
      return AdminPage;
    default:
      return NotFoundPage;
  }
}

function protectedPath(route) {
  const first = route.segments[0];
  return ["orders", "tasks", "entitlements", "media", "mine", "create", "admin"].indexOf(first) >= 0;
}

// 路由状态在应用级共享：根实例与所有页面读取同一响应式对象，hash 变化时就地更新。
const routeState = reactive(parseRoute());

const app = createApp({
  data() {
    return {
      route: routeState,
      routeEpoch: 0,
      token: getToken(),
      user: getUser(),
      sessionNotice: false,
      toast: null,
      toastTimer: null,
      searchText: "",
      clock: Date.now(),
      apiUnavailable: false,
    };
  },
  computed: {
    page() {
      return resolvePage(this.route);
    },
    isAdmin() {
      return isAdmin(this.user);
    },
    initial() {
      const name = (this.user && this.user.nickName) || "我";
      return name.slice(0, 1);
    },
    loginLink() {
      return "#/login?returnTo=" + encodeURIComponent(this.route.fullPath || "#/home");
    },
    navSection() {
      const first = this.route.segments[0] || "home";
      if (first === "creations") return "community";
      return first;
    },
  },
  created() {
    this.request = createApi({
      tokenProvider: () => this.token,
      onUnauthorized: () => this.expireSession(),
      timeout: 15000,
    });
    window.addEventListener("hashchange", this.onHashChange);
    window.addEventListener("aiplatform:session-expired", this.onSessionExpired);
  },
  mounted() {
    this.applyTitle();
    if (this.token) {
      this.verifySession(false).catch(() => {
        // 公共页面保持可用；受保护请求会各自给出明确失败原因。
      });
    }
  },
  beforeUnmount() {
    window.removeEventListener("hashchange", this.onHashChange);
    window.removeEventListener("aiplatform:session-expired", this.onSessionExpired);
    if (this.toastTimer) window.clearTimeout(this.toastTimer);
  },
  methods: {
    parseRoute,
    onHashChange() {
      Object.assign(this.route, parseRoute());
      this.routeEpoch++;
      this.applyTitle();
      const main = document.getElementById("main");
      if (main) main.focus({ preventScroll: true });
      window.scrollTo({ top: 0, behavior: "auto" });
    },
    onSessionExpired() {
      this.expireSession();
    },
    applyTitle() {
      const first = this.route.segments[0] || "home";
      const title = ROUTE_TITLES[first];
      document.title = (title ? title + " · " : "") + "AI 创作平台";
    },
    notify(text, tone = "info") {
      if (this.toastTimer) window.clearTimeout(this.toastTimer);
      this.toast = { text, tone };
      this.toastTimer = window.setTimeout(() => {
        this.toast = null;
        this.toastTimer = null;
      }, 4200);
    },
    search() {
      const keyword = this.searchText.trim();
      const next = "#/apps" + (keyword ? "?keyword=" + encodeURIComponent(keyword) : "");
      if (window.location.hash === next) {
        this.onHashChange();
      } else {
        window.location.hash = next;
      }
    },
    navActive(section) {
      return this.navSection === section;
    },
    purchaseKey(scope) {
      return requestKey(scope);
    },
    rememberPurchaseKey(scope, key) {
      rememberRequestKey(scope, key);
    },
    clearPurchaseKey(scope) {
      clearRequestKey(scope);
    },
    async verifySession(required) {
      if (!this.token) return null;
      try {
        const user = await this.request("/user/me", { quiet: true });
        this.user = user.data;
        saveUser(user.data);
        return user.data;
      } catch (failure) {
        if (failure.status === 401) {
          this.expireSession();
          return null;
        }
        if (required) throw failure;
        return null;
      }
    },
    async completeLogin(token) {
      if (!token) throw new Error("登录响应缺少会话令牌");
      saveSession(token, null);
      this.token = token;
      this.user = null;
      this.sessionNotice = false;
      const user = await this.verifySession(true);
      if (!user) throw new Error("登录状态未能确认，请重新登录");
      return user;
    },
    async loadSession() {
      return this.verifySession(true);
    },
    expireSession() {
      const hadSession = Boolean(this.token || this.user);
      this.token = "";
      this.user = null;
      clearSession();
      if (hadSession && !this.sessionNotice) {
        this.sessionNotice = true;
        this.notify("登录已过期，请重新登录后继续操作。", "error");
      }
    },
    async logout() {
      try {
        if (this.token) await this.request("/user/logout", { method: "POST", quiet: true });
      } catch {
        // 退出以本地会话清除为准；服务端令牌可能已过期。
      }
      this.token = "";
      this.user = null;
      this.sessionNotice = false;
      clearSession();
      this.notify("已退出登录。");
      if (protectedPath(this.route)) window.location.hash = "#/home";
      else this.onHashChange();
    },
  },
  template: `
    <div>
      <a class="skip-link" href="#main">跳到主要内容</a>
      <header class="topbar">
        <a class="brand" href="#/home">AI <span>创作平台</span></a>
        <nav class="main-nav" aria-label="主导航">
          <a href="#/home" :class="{ active: navActive('home') }">首页</a>
          <a href="#/apps" :class="{ active: navActive('apps') }">应用</a>
          <a href="#/campaigns" :class="{ active: navActive('campaigns') }">限量活动</a>
          <a href="#/community" :class="{ active: navActive('community') }">社区</a>
        </nav>
        <form class="header-search" role="search" @submit.prevent="search">
          <input v-model.trim="searchText" maxlength="50" placeholder="搜索 AI 应用" aria-label="搜索应用" />
          <button type="submit">搜索</button>
        </form>
        <div class="account">
          <template v-if="user">
            <a class="avatar" href="#/mine" :title="user.nickName">{{ initial }}</a>
            <a href="#/mine">{{ user.nickName }}</a>
            <a href="#/orders">订单</a>
            <a href="#/tasks">任务</a>
            <a href="#/entitlements">权益</a>
            <a v-if="isAdmin" href="#/admin">运营台</a>
            <button class="button ghost small" type="button" @click="logout">退出</button>
          </template>
          <a v-else class="button small" :href="loginLink">登录</a>
        </div>
      </header>

      <div v-if="sessionNotice" class="session-banner" role="alert">
        <span>登录已过期或已退出；受保护页面不会用旧数据冒充成功，请重新登录。</span>
        <span class="actions">
          <a class="button small" :href="loginLink">重新登录</a>
          <button class="button ghost small" type="button" @click="sessionNotice = false">关闭提示</button>
        </span>
      </div>

      <main id="main" class="shell" tabindex="-1">
        <component :is="page" :key="route.fullPath + ':' + routeEpoch"></component>
      </main>

      <footer class="site-footer">
        当前版本提供应用浏览、使用包购买、限量活动、模拟支付、作品与素材管理，以及固定规格提交、排队与次数冻结；购买或领取完成后异步发放权益，是否到账、是否过期以真实查询结果为准，已支付不等于已到账；AI 执行、结算、真实支付、退款与评论尚未开放。
      </footer>

      <div v-if="toast" class="toast" :class="toast.tone" role="status">{{ toast.text }}</div>
    </div>
  `,
});

// Vue 3 用 globalProperties 提供与 Vue.prototype 等价的共享方法；函数内 this 仍指向调用组件。
app.config.globalProperties.$api = function api(path, options) {
  return this.$root.request(path, options);
};
app.config.globalProperties.$authorizedHeaders = function authorizedHeaders() {
  return this.$root.token ? { authorization: this.$root.token } : {};
};
app.config.globalProperties.$formatTime = formatTime;
app.config.globalProperties.$formatDate = formatDate;
app.config.globalProperties.$parseServerTime = parseServerTime;
app.config.globalProperties.$countdown = countdown;
app.config.globalProperties.$isExpired = isExpired;
app.config.globalProperties.$orderStatus = orderStatusLabel;
app.config.globalProperties.$reservation = reservationLabel;
app.config.globalProperties.$route = routeState;

registerComponents(app);
app.mount("#app");

export { app };

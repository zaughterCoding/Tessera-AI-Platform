const pageCommon = {
  data() {
    return { loading: false, error: null };
  },
  computed: {
    isLoggedIn() {
      return Boolean(this.$root.user);
    },
  },
  methods: {
    fail(reason) {
      this.error = reason;
      if (reason && reason.status === 401) {
        this.$root.notify("登录已过期，请重新登录后再操作。", "error");
      } else {
        this.$root.notify((reason && reason.message) || "请求未完成", "error");
      }
    },
    loginLink() {
      return "#/login?returnTo=" + encodeURIComponent(this.$route.fullPath);
    },
    go(path) {
      window.location.hash = path;
    },
  },
};

const HomePage = {
  mixins: [pageCommon],
  data() {
    return {
      categories: [],
      applications: [],
      popular: [],
      popularError: null,
      offers: [],
      creations: [],
    };
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        const [categories, applications, popular, offers, creations] = await Promise.all([
          this.$api("/catalog/categories"),
          this.$api("/catalog/applications", { params: { sort: "newest", size: 6 } }),
          this.$api("/analytics/popularity", { params: { minutes: 5, limit: 6 } }).catch((e) => {
            this.popularError = e;
            return null;
          }),
          this.$api("/campaign/offers").catch((e) => {
            if (e.status !== 401) this.error = e;
            return null;
          }),
          this.$api("/creations/hot", { params: { limit: 6 } }).catch(() => null),
        ]);
        this.categories = categories.data || [];
        this.applications = applications.data || [];
        this.popular = popular ? popular.data || [] : [];
        this.offers = offers ? (offers.data || []).filter((item) => item.sellable).slice(0, 4) : [];
        this.creations = creations ? creations.data || [] : [];
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    categoryLink(category) {
      return "#/apps?categoryId=" + encodeURIComponent(category.id);
    },
    appLink(item) {
      return "#/apps/" + encodeURIComponent(item.applicationId || item.id);
    },
    creationLink(item) {
      return "#/creations/" + encodeURIComponent(item.id);
    },
  },
  template: `
    <div>
      <section class="hero">
        <div class="hero-copy">
          <span class="eyebrow">AI 创作 · 使用包与作品社区</span>
          <h1>挑一个 AI 应用，<br />把灵感变成作品。</h1>
          <p>浏览应用、购买使用包、参与限量活动，也可以先看看别人用同样的模型做出了什么。</p>
          <div class="actions">
            <a class="button" href="#/apps">开始浏览</a>
            <a class="button ghost" href="#/community">逛作品社区</a>
          </div>
        </div>
        <div class="hero-panel">
          <div>
            <span class="eyebrow" style="color:#b9bcf5">真实状态</span>
            <p>下单、受理、支付与权益到账分别展示；页面不会把 HTTP 200 当作业务成功。</p>
          </div>
          <div>
            <div class="stat">异步发放</div>
            <small>购买或领取完成后异步发放权益；是否到账、是否过期以真实查询为准，已支付不等于已到账。</small>
          </div>
        </div>
      </section>

      <div class="category-strip" aria-label="应用分类">
        <a v-for="category in categories" :key="category.id" :href="categoryLink(category)">{{ category.name }}</a>
      </div>

      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>

      <section v-if="!loading" class="section">
        <div class="section-head">
          <h2>最近上架</h2>
          <a href="#/apps?sort=newest">查看全部</a>
        </div>
        <div class="grid grid-3">
          <a v-for="item in applications" :key="item.id" class="app-card" :href="'#/apps/' + item.id">
            <app-image :src="item.coverUrl" :alt="item.name"></app-image>
            <div class="body">
              <h3>{{ item.name }}</h3>
              <p>{{ item.summary }}</p>
              <div class="meta"><span>{{ item.categoryName || '未分类' }}</span><span class="price">¥{{ (item.priceFrom / 100).toFixed(2) }} 起</span></div>
            </div>
          </a>
        </div>
        <empty-block v-if="!applications.length" text="暂时没有已发布的应用"></empty-block>
      </section>

      <section v-if="!loading" class="section">
        <div class="section-head">
          <h2>当前热榜</h2>
          <span class="muted">最近 5 分钟真实行为聚合；分析未开启时不会显示为 0。</span>
        </div>
        <error-block v-if="popularError" :error="popularError" action-label="重新获取" @retry="load"></error-block>
        <div v-else class="grid grid-3">
          <a v-for="item in popular" :key="item.applicationId" class="app-card" :href="appLink(item)">
            <app-image :src="item.coverUrl" :alt="item.name"></app-image>
            <div class="body">
              <h3>#{{ item.rank }} {{ item.name }}</h3>
              <p>{{ item.summary }}</p>
              <div class="meta"><span>热度 {{ item.heat }}</span><span>访问 {{ item.visits }} · 支付 {{ item.payments }}</span></div>
            </div>
          </a>
        </div>
        <empty-block v-if="!popularError && !popular.length" text="热榜窗口内还没有行为数据"></empty-block>
      </section>

      <section v-if="!loading && offers.length" class="section">
        <div class="section-head">
          <h2>进行中的限量活动</h2>
          <a href="#/campaigns">全部活动</a>
        </div>
        <div class="grid grid-2">
          <a v-for="offer in offers" :key="offer.id" class="card" :href="'#/campaigns/' + offer.id" style="text-decoration:none;color:inherit">
            <div class="meta-line"><status-badge :status="offer.state" :label="offer.displayNotice"></status-badge></div>
            <h3 style="margin:8px 0 4px">{{ offer.applicationName }} · {{ offer.productTitle }}</h3>
            <div class="meta-line"><span>{{ offer.credits }} 次</span><span>剩余 {{ offer.remainingStock }} 份</span><span class="price">¥{{ (offer.price / 100).toFixed(2) }}</span></div>
          </a>
        </div>
      </section>

      <section v-if="!loading && creations.length" class="section">
        <div class="section-head">
          <h2>社区热作</h2>
          <a href="#/community">进入社区</a>
        </div>
        <div class="grid grid-3">
          <a v-for="item in creations" :key="item.id" class="app-card" :href="creationLink(item)">
            <app-image v-if="item.media && item.media.length" :src="item.media[0].url" :alt="item.title"></app-image>
            <div class="body">
              <h3>{{ item.title }}</h3>
              <div class="muted">{{ item.applicationName }} · {{ item.kind === 'WORK' ? '作品' : '测评' }}</div>
              <div class="meta"><span>{{ item.authorNickName }}</span><span>♥ {{ item.likeCount }}</span></div>
            </div>
          </a>
        </div>
      </section>
    </div>
  `,
};

const AppsPage = {
  mixins: [pageCommon],
  data() {
    return {
      categories: [],
      records: [],
      total: 0,
      page: 1,
      pageSize: 12,
      keyword: "",
      categoryId: "",
      sort: "score",
    };
  },
  created() {
    this.keyword = this.$route.query.keyword || "";
    this.categoryId = this.$route.query.categoryId || "";
    this.sort = this.$route.query.sort || "score";
    this.page = Number(this.$route.query.page || 1);
    this.loadCategories();
    this.load();
  },
  methods: {
    async loadCategories() {
      try {
        const result = await this.$api("/catalog/categories");
        this.categories = result.data || [];
      } catch {
        this.categories = [];
      }
    },
    async load() {
      await this.run(async () => {
        const result = await this.$api("/catalog/applications", {
          params: {
            keyword: this.keyword,
            categoryId: this.categoryId,
            sort: this.sort,
            page: this.page,
            size: this.pageSize,
          },
        });
        this.records = result.data || [];
        this.total = Number(result.total || 0);
      });
    },
    async run(task) {
      this.loading = true;
      this.error = null;
      try {
        await task();
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    search() {
      this.page = 1;
      this.sync();
    },
    selectCategory(id) {
      this.categoryId = id;
      this.page = 1;
      this.sync();
    },
    changeSort() {
      this.page = 1;
      this.sync();
    },
    sync() {
      const query = new URLSearchParams();
      if (this.keyword) query.set("keyword", this.keyword);
      if (this.categoryId) query.set("categoryId", this.categoryId);
      if (this.sort) query.set("sort", this.sort);
      if (this.page > 1) query.set("page", String(this.page));
      const text = query.toString();
      const next = "#/apps" + (text ? "?" + text : "");
      // 路由变化会重建页面；相同地址则原地重新读取，避免重复请求。
      if (window.location.hash === next) this.load();
      else window.location.hash = next;
    },
    turn(delta) {
      const next = this.page + delta;
      if (next < 1 || (next - 1) * this.pageSize >= this.total) return;
      this.page = next;
      this.sync();
    },
  },
  computed: {
    totalPages() {
      return Math.max(1, Math.ceil(this.total / this.pageSize));
    },
  },
  template: `
    <div>
      <div class="section-head">
        <h2>AI 应用</h2>
        <span class="muted">共 {{ total }} 个已发布应用</span>
      </div>
      <div class="filters">
        <label class="field" style="flex:1;min-width:200px">
          <span>关键词</span>
          <input v-model.trim="keyword" maxlength="50" placeholder="按名称、简介或能力搜索" @keyup.enter="search" />
        </label>
        <label class="field">
          <span>排序</span>
          <select v-model="sort" @change="changeSort">
            <option value="score">综合评分</option>
            <option value="price">价格从低到高</option>
            <option value="newest">最新发布</option>
          </select>
        </label>
        <button class="button" type="button" @click="search">搜索</button>
      </div>
      <div class="category-strip" style="margin-bottom:18px">
        <a href="javascript:void(0)" :class="{ active: !categoryId }" @click.prevent="selectCategory('')">全部分类</a>
        <a v-for="category in categories" :key="category.id" href="javascript:void(0)"
          :class="{ active: categoryId === category.id }" @click.prevent="selectCategory(category.id)">{{ category.name }}</a>
      </div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="!loading && !error" class="grid grid-3">
        <a v-for="item in records" :key="item.id" class="app-card" :href="'#/apps/' + item.id">
          <app-image :src="item.coverUrl" :alt="item.name"></app-image>
          <div class="body">
            <h3>{{ item.name }}</h3>
            <p>{{ item.summary }}</p>
            <div class="chips"><span class="chip">{{ item.categoryName || '未分类' }}</span><span class="chip">评分 {{ item.score }}</span></div>
            <div class="meta"><span>{{ item.soldCount }} 次购买</span><span class="price">¥{{ (item.priceFrom / 100).toFixed(2) }} 起</span></div>
          </div>
        </a>
      </div>
      <empty-block v-if="!loading && !error && !records.length" text="没有符合条件的应用，试试其他关键词或分类"></empty-block>
      <div v-if="!loading && totalPages > 1" class="actions" style="justify-content:center;margin-top:20px">
        <button class="button ghost small" type="button" :disabled="page <= 1" @click="turn(-1)">上一页</button>
        <span class="muted">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="button ghost small" type="button" :disabled="page >= totalPages" @click="turn(1)">下一页</button>
      </div>
    </div>
  `,
};

const AppDetailPage = {
  mixins: [pageCommon],
  data() {
    return {
      id: this.$route.id,
      detail: null,
      products: [],
      creations: [],
      offers: [],
      activity: null,
      activityError: null,
      buying: "",
      purchaseError: null,
      generationStatus: null,
      generationStatusError: null,
      generationLoading: false,
      generationError: null,
      grants: [],
      specOptions: [],
      generation: { selected: "", prompt: "", negativePrompt: "", mediaIds: [] },
      generationAssets: [],
      generationAssetsError: null,
      submitting: false,
    };
  },
  created() {
    this.load();
    this.loadGeneration();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        const [detail, products, creations, offers] = await Promise.all([
          this.$api("/catalog/applications/" + encodeURIComponent(this.id)),
          this.$api("/catalog/applications/" + encodeURIComponent(this.id) + "/products"),
          this.$api("/creations/by-application/" + encodeURIComponent(this.id), { params: { limit: 6 } }).catch(() => null),
          this.$api("/campaign/offers").catch(() => null),
        ]);
        this.detail = detail.data;
        this.products = products.data || [];
        this.creations = creations ? creations.data || [] : [];
        this.offers = offers
          ? (offers.data || []).filter((offer) => String(offer.applicationId) === String(this.id))
          : [];
        this.loadActivity();
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    async loadActivity() {
      try {
        const result = await this.$api("/analytics/applications/" + encodeURIComponent(this.id) + "/overview", {
          params: { minutes: 5 },
        });
        this.activity = result.data;
      } catch (failure) {
        this.activityError = failure;
      }
    },
    async buy(product) {
      if (!this.isLoggedIn) {
        this.go(this.loginLink());
        return;
      }
      if (!product.orderable) {
        this.purchaseError = new Error("该商品当前不可直接购买，请查看限量活动或稍后再试。");
        return;
      }
      this.buying = product.id;
      this.purchaseError = null;
      const key = this.$root.purchaseKey("product:" + product.id);
      try {
        const result = await this.$api("/trade/orders", {
          method: "POST",
          body: { productId: product.id, requestKey: key },
        });
        this.$root.clearPurchaseKey("product:" + product.id);
        this.$root.notify("订单已创建，状态为待支付；请继续完成模拟支付。");
        this.go("#/orders/" + encodeURIComponent(result.data.id));
      } catch (failure) {
        this.purchaseError = failure;
        if (failure.status === 401) this.fail(failure);
      } finally {
        this.buying = "";
      }
    },
    offerLink(offer) {
      return "#/campaigns/" + encodeURIComponent(offer.id);
    },
    async loadGeneration() {
      this.generationLoading = true;
      try {
        const status = await this.$api("/generation/status");
        this.generationStatus = status.data;
      } catch (failure) {
        this.generationStatusError = failure;
      }
      if (this.$root.user) {
        try {
          const grants = await this.$api("/entitlement/grants", { params: { size: 50 } });
          this.grants = grants.data || [];
          this.specOptions = this.buildSpecOptions();
          if (this.specOptions.length) this.generation.selected = this.specOptions[0].key;
        } catch (failure) {
          this.generationError = failure;
        }
        try {
          const assets = await this.$api("/media/mine", { params: { limit: 50, offset: 0 } });
          this.generationAssets = (assets.data || []).filter((asset) => asset.state === "ACTIVE");
        } catch (failure) {
          this.generationAssetsError = failure;
        }
      }
      this.generationLoading = false;
    },
    // 固定规格只从已到账权益包快照推导；页面不提供模型、张数或冻结次数的自选。
    buildSpecOptions() {
      const seen = {};
      const options = [];
      (this.grants || []).forEach((grant) => {
        if (grant.status !== "ACTIVE" || Number(grant.availableCredits) <= 0) return;
        const key = [
          grant.applicationVersion,
          grant.modelVersion,
          grant.imageSize,
          grant.outputCount,
        ].join("|");
        if (seen[key]) return;
        seen[key] = true;
        options.push({
          key,
          applicationVersion: grant.applicationVersion,
          modelVersion: grant.modelVersion,
          imageSize: grant.imageSize,
          outputCount: grant.outputCount,
          available: Number(grant.availableCredits),
        });
      });
      return options;
    },
    toggleGenerationMedia(id) {
      const index = this.generation.mediaIds.indexOf(id);
      if (index >= 0) {
        this.generation.mediaIds.splice(index, 1);
        return;
      }
      if (this.generation.mediaIds.length >= 4) {
        this.$root.notify("最多选择 4 个参考素材。", "error");
        return;
      }
      this.generation.mediaIds.push(id);
    },
    // 同一业务请求的重试必须复用同一请求键；内容或规格变化后自然换新键。
    generationScope() {
      const spec = this.selectedSpec;
      const signature = [
        "generation",
        String(this.id),
        spec ? spec.applicationVersion : "",
        spec ? spec.modelVersion : "",
        spec ? spec.imageSize : "",
        spec ? String(spec.outputCount) : "",
        (this.generation.prompt || "").trim(),
        (this.generation.negativePrompt || "").trim(),
        this.generation.mediaIds.join(","),
      ];
      return "generation:" + JSON.stringify(signature);
    },
    generationMessage(failure) {
      const code = failure && failure.code;
      if (code === "REQUEST_CONFLICT") return "该请求键已用于不同的业务请求；请修改内容后重新提交。";
      if (code === "INSUFFICIENT_CREDITS") return "当前规格没有可用的权益次数；请先购买或等待发放。";
      if (code === "SPEC_MISMATCH") return "没有与所选应用、版本和固定规格匹配的权益包。";
      if (code === "APPLICATION_UNAVAILABLE") return "该应用或版本当前不可提交，请返回应用列表重试。";
      if (code === "SUBMISSION_BUSY") return "提交请求繁忙，请稍后重试；同一请求重试不会重复冻结。";
      if (code === "SUBMISSION_DISABLED") return "当前环境未开启任务提交。";
      if (code === "INVALID_REQUEST") return "请求参数不合法，请检查提示词、素材与所选规格。";
      return (failure && failure.message) || "提交未完成";
    },
    async submitGeneration() {
      if (!this.isLoggedIn) {
        this.go(this.loginLink());
        return;
      }
      const spec = this.selectedSpec;
      const prompt = (this.generation.prompt || "").trim();
      if (!spec) {
        this.$root.notify("没有可用的固定规格权益包。", "error");
        return;
      }
      if (!prompt) {
        this.$root.notify("请填写提示词。", "error");
        return;
      }
      if (!this.generationStatus || !this.generationStatus.submitEnabled) {
        this.$root.notify("当前环境未开启任务提交。", "error");
        return;
      }
      this.submitting = true;
      this.generationError = null;
      const scope = this.generationScope();
      const key = this.$root.purchaseKey(scope);
      try {
        const result = await this.$api("/generation/tasks", {
          method: "POST",
          body: {
            requestKey: key,
            applicationId: Number(this.detail.id),
            applicationVersion: spec.applicationVersion,
            modelVersion: spec.modelVersion,
            imageSize: spec.imageSize,
            outputCount: spec.outputCount,
            prompt,
            negativePrompt: (this.generation.negativePrompt || "").trim(),
            mediaIds: this.generation.mediaIds.slice(),
          },
        });
        this.$root.clearPurchaseKey(scope);
        this.$root.notify("任务已受理，次数已冻结；AI 执行尚未开始。");
        this.go("#/tasks/" + encodeURIComponent(result.data.id));
      } catch (failure) {
        this.generationError = failure;
        if (failure.status === 401) this.fail(failure);
        else this.$root.notify(this.generationMessage(failure), "error");
      } finally {
        this.submitting = false;
      }
    },
  },
  computed: {
    selectedSpec() {
      return this.specOptions.find((spec) => spec.key === this.generation.selected) || null;
    },
    generationEnabled() {
      return Boolean(this.generationStatus && this.generationStatus.submitEnabled);
    },
  },
  template: `
    <div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="detail" class="detail-layout">
        <div>
          <div class="card">
            <div class="meta-line"><span class="chip">{{ detail.categoryName || '未分类' }}</span><span>版本 {{ detail.applicationVersion }}</span></div>
            <h1 style="margin:10px 0 6px">{{ detail.name }}</h1>
            <p class="muted">{{ detail.summary }}</p>
            <div class="meta-line"><span>模型 {{ detail.modelName }} {{ detail.modelVersion }}</span><span>评分 {{ detail.score }}</span><span>已售 {{ detail.soldCount }}</span></div>
            <hr style="border:0;border-top:1px solid #efeee9;margin:18px 0" />
            <h3>应用介绍</h3>
            <p class="content-pre">{{ detail.description }}</p>
            <h3>能力</h3>
            <p class="content-pre">{{ detail.capabilities }}</p>
          </div>

          <section class="section">
            <div class="section-head"><h2>使用包</h2><span class="muted">价格为整数分，展示为人民币；支付完成后异步发放权益，到账与过期以真实查询为准</span></div>
            <div v-for="product in products" :key="product.id" class="product-card">
              <div>
                <h4>{{ product.title }}</h4>
                <div class="muted">{{ product.subtitle }}</div>
                <div class="meta-line" style="margin-top:6px">
                  <span>{{ product.credits }} 次</span><span>{{ product.validDays }} 天有效</span>
                  <span v-if="product.saleMode === 1" class="chip">限量商品</span>
                  <span v-else class="chip">标准商品</span>
                </div>
                <div class="muted content-pre">{{ product.rules }}</div>
              </div>
              <div style="text-align:right">
                <div class="price" style="margin-bottom:8px">¥{{ (product.price / 100).toFixed(2) }}</div>
                <button v-if="product.orderable" class="button" type="button" :disabled="buying === product.id" @click="buy(product)">
                  {{ buying === product.id ? '创建中…' : '购买' }}
                </button>
                <span v-else class="muted">当前不可直接购买</span>
              </div>
            </div>
            <empty-block v-if="!products.length" text="该应用暂时没有可售使用包"></empty-block>
            <notice-box v-if="purchaseError" tone="danger" title="购买未完成">
              {{ purchaseError.message }}
              <span v-if="purchaseError.requestId" class="muted">请求编号 {{ purchaseError.requestId }}</span>
            </notice-box>
            <notice-box tone="info" title="权益说明">购买或领取完成后异步发放权益；是否到账、是否过期以真实查询结果为准，已支付不等于已到账；任务提交只冻结一次权益次数，AI 执行与结算尚未开放。</notice-box>
          </section>

          <section class="section">
            <div class="section-head"><h2>提交生成任务</h2><a href="#/tasks">我的任务</a></div>
            <notice-box v-if="!isLoggedIn" tone="info" title="需要登录">
              <a :href="loginLink()">登录</a>后可用本人已到账权益提交固定规格请求。
            </notice-box>
            <notice-box v-else-if="generationStatusError" tone="warn" title="提交状态未知">
              暂时无法确认提交开关；请稍后重试。既有任务与权益仍可查询。
            </notice-box>
            <notice-box v-else-if="generationStatus && !generationStatus.submitEnabled" tone="warn" title="提交当前未开启">
              {{ generationStatus.notice }}既有任务、冻结与权益仍可查询。
            </notice-box>
            <loading-block v-if="isLoggedIn && generationLoading" text="读取本人权益与素材…"></loading-block>
            <div v-else-if="isLoggedIn && generationEnabled" class="card">
              <empty-block v-if="!specOptions.length" text="当前应用没有可用权益；购买或领取到账后可提交"></empty-block>
              <template v-else>
                <label class="field">
                  <span>固定规格（来自已到账权益包，模型与张数不可修改）</span>
                  <select v-model="generation.selected">
                    <option v-for="spec in specOptions" :key="spec.key" :value="spec.key">
                      版本 {{ spec.applicationVersion }} · {{ spec.modelVersion }} · {{ spec.imageSize }} · {{ spec.outputCount }} 张 · 可用 {{ spec.available }} 次
                    </option>
                  </select>
                </label>
                <label class="field" style="margin-top:12px">
                  <span>提示词（必填，最多 2000 字符）</span>
                  <textarea v-model="generation.prompt" maxlength="2000" placeholder="描述希望生成的内容"></textarea>
                </label>
                <label class="field" style="margin-top:12px">
                  <span>排除内容（可选，最多 1000 字符）</span>
                  <textarea v-model="generation.negativePrompt" maxlength="1000" style="min-height:70px" placeholder="不希望出现的内容"></textarea>
                </label>
                <div v-if="generationAssets.length" style="margin-top:12px">
                  <p class="muted">参考素材（本人有效素材，最多 4 个；素材保持私有，不会被公开）</p>
                  <div class="grid grid-3">
                    <button v-for="asset in generationAssets" :key="asset.id" type="button" class="app-card" :class="{ 'is-selected': generation.mediaIds.indexOf(asset.id) >= 0 }" style="cursor:pointer;text-align:left;padding:0" @click="toggleGenerationMedia(asset.id)">
                      <app-image :src="asset.url" :alt="asset.originalName"></app-image>
                      <div class="body"><div class="muted">{{ asset.originalName }}</div></div>
                    </button>
                  </div>
                </div>
                <notice-box v-if="generationAssetsError" tone="warn" title="素材未加载">
                  暂时无法读取本人素材；可先不带素材提交。
                </notice-box>
                <notice-box v-if="generationError" tone="danger" title="提交未完成">
                  {{ generationMessage(generationError) }}
                  <span v-if="generationError.requestId" class="muted">请求编号 {{ generationError.requestId }}</span>
                </notice-box>
                <notice-box tone="info" title="当前阶段边界">
                  提交成功后任务进入排队，权益包冻结 1 次；AI 执行尚未开始。页面不轮询不存在的进度，也不提供取消、释放、结算或结果下载。
                </notice-box>
                <div class="actions" style="margin-top:12px">
                  <button class="button" type="button" :disabled="submitting" @click="submitGeneration">
                    {{ submitting ? '提交中…' : '提交任务' }}
                  </button>
                  <span class="muted">网络失败后保留原请求键重试；成功回执只返回同一个任务，不会重复冻结。</span>
                </div>
              </template>
            </div>
          </section>

          <section v-if="offers.length" class="section">
            <div class="section-head"><h2>限量活动</h2><a href="#/campaigns">查看所有活动</a></div>
            <div class="grid grid-2">
              <a v-for="offer in offers" :key="offer.id" class="card" :href="offerLink(offer)" style="text-decoration:none;color:inherit">
                <status-badge :status="offer.state" :label="offer.state"></status-badge>
                <h4 style="margin:8px 0 4px">{{ offer.productTitle }}</h4>
                <div class="meta-line"><span>剩余 {{ offer.remainingStock }} 份</span><span class="price">¥{{ (offer.price / 100).toFixed(2) }}</span></div>
              </a>
            </div>
          </section>

          <section v-if="creations.length" class="section">
            <div class="section-head"><h2>相关作品</h2><a :href="'#/community?applicationId=' + detail.id">查看更多</a></div>
            <div class="grid grid-3">
              <a v-for="item in creations" :key="item.id" class="app-card" :href="'#/creations/' + item.id">
                <app-image v-if="item.media && item.media.length" :src="item.media[0].url" :alt="item.title"></app-image>
                <div class="body"><h3>{{ item.title }}</h3><div class="muted">{{ item.authorNickName }}</div></div>
              </a>
            </div>
          </section>
        </div>

        <aside class="sticky-side">
          <app-image :src="detail.coverUrl" :alt="detail.name" ratio="16 / 10"></app-image>
          <div class="card">
            <h3 style="margin-top:0">最近 5 分钟</h3>
            <error-block v-if="activityError" :error="activityError" action-label="重试" @retry="loadActivity"></error-block>
            <dl v-else-if="activity" class="kv">
              <dt>访问</dt><dd>{{ activity.visits }}</dd>
              <dt>独立访客</dt><dd>{{ activity.uv }}</dd>
              <dt>订单</dt><dd>{{ activity.orders }}</dd>
              <dt>支付</dt><dd>{{ activity.payments }}</dd>
              <dt>活动受理</dt><dd>{{ activity.claims }}</dd>
              <dt>热度</dt><dd>{{ activity.heat }}</dd>
            </dl>
            <div class="muted">行为数据来自真实 Kafka 聚合；未开启分析时不会伪造 0。</div>
          </div>
          <notice-box tone="warn" title="当前版本边界">权益到账与过期状态按真实查询展示；已受理任务只排队并冻结次数，AI 执行、结算、真实支付、退款与评论尚未开放。</notice-box>
        </aside>
      </div>
    </div>
  `,
};

const CampaignsPage = {
  mixins: [pageCommon],
  data() {
    return { offers: [] };
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        const result = await this.$api("/campaign/offers");
        this.offers = result.data || [];
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
  },
  template: `
    <div>
      <div class="section-head"><h2>限量活动</h2><span class="muted">受理不等于建单成功；建单成功也不等于已支付</span></div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div class="grid grid-2">
        <a v-for="offer in offers" :key="offer.id" class="card" :href="'#/campaigns/' + offer.id" style="text-decoration:none;color:inherit">
          <div class="meta-line"><status-badge :status="offer.state" :label="offer.state"></status-badge><span class="chip">{{ offer.applicationName }}</span></div>
          <h3 style="margin:10px 0 4px">{{ offer.productTitle }}</h3>
          <p class="muted">{{ offer.productSubtitle }}</p>
          <div class="meta-line"><span>{{ offer.credits }} 次 / {{ offer.validDays }} 天</span><span>剩余 {{ offer.remainingStock }} 份</span><span class="price">¥{{ (offer.price / 100).toFixed(2) }}</span></div>
          <p class="muted">{{ offer.displayNotice }}</p>
        </a>
      </div>
      <empty-block v-if="!loading && !offers.length" text="暂时没有已发布的活动"></empty-block>
    </div>
  `,
};

const CampaignDetailPage = {
  mixins: [pageCommon],
  data() {
    return {
      id: this.$route.id,
      offer: null,
      admission: null,
      accepting: false,
      pollTimer: null,
      polling: false,
      pollAttempts: 0,
      pollDelay: 2000,
      pollTimeout: false,
      notice: "",
      unmounted: false,
    };
  },
  created() {
    this.load();
  },
  beforeUnmount() {
    this.unmounted = true;
    this.stopPolling();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        const result = await this.$api("/campaign/offers/" + encodeURIComponent(this.id));
        this.offer = result.data;
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    key() {
      return this.$root.purchaseKey("campaign:" + this.id);
    },
    async admit() {
      if (!this.isLoggedIn) {
        this.go(this.loginLink());
        return;
      }
      this.accepting = true;
      this.pollTimeout = false;
      this.error = null;
      try {
        const result = await this.$api("/campaign/reservations", {
          method: "POST",
          body: { campaignId: this.offer.id, requestKey: this.key() },
        });
        // 受理响应可能晚于路由切换返回；已卸载时不再启动新的轮询。
        if (this.unmounted) return;
        this.admission = result.data;
        if (result.data.requestKey) {
          this.$root.rememberPurchaseKey("campaign:" + this.id, result.data.requestKey);
        }
        if (["SUCCESS", "FAILED", "REVIEW"].indexOf(result.data.state) >= 0) {
          this.$root.clearPurchaseKey("campaign:" + this.id);
          this.$root.notify(result.data.reservationNotice || "预约状态已更新");
        } else {
          this.$root.notify("已受理预购请求；受理不代表建单成功或已付款。");
          this.startPolling(result.data.requestKey || this.key());
        }
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.accepting = false;
      }
    },
    startPolling(key) {
      if (this.unmounted) return;
      this.stopPolling();
      this.polling = true;
      this.pollAttempts = 0;
      this.pollDelay = 2000;
      this.pollTimer = window.setTimeout(() => this.poll(key), this.pollDelay);
    },
    stopPolling() {
      this.polling = false;
      if (this.pollTimer) {
        window.clearTimeout(this.pollTimer);
        this.pollTimer = null;
      }
    },
    async poll(key) {
      if (!this.polling) return;
      this.pollAttempts++;
      try {
        const result = await this.$api("/campaign/reservations/by-request", {
          params: { campaignId: this.offer.id, requestKey: key },
          quiet: true,
        });
        if (!this.polling) return;
        this.admission = result.data;
        this.pollDelay = 2000;
        if (["SUCCESS", "FAILED", "REVIEW"].indexOf(result.data.state) >= 0) {
          this.stopPolling();
          this.$root.clearPurchaseKey("campaign:" + this.id);
          this.$root.notify(result.data.reservationNotice || "预约状态已更新");
          return;
        }
      } catch (failure) {
        if (failure.status === 401) {
          this.stopPolling();
          this.$root.notify("登录已过期，已停止查询预约结果。", "error");
          return;
        }
        if (failure.status === 429 || failure.status === 503) {
          this.pollDelay = 5000;
        }
      }
      // 请求返回前组件可能已卸载；此时不再续约定时器。
      if (!this.polling) return;
      if (this.pollAttempts >= 30) {
        this.stopPolling();
        this.pollTimeout = true;
        return;
      }
      this.pollTimer = window.setTimeout(() => this.poll(key), this.pollDelay);
    },
    queryOnce() {
      if (!this.isLoggedIn) {
        this.go(this.loginLink());
        return;
      }
      this.polling = true;
      this.pollAttempts = 0;
      this.poll(this.key());
    },
    retryNewIntent() {
      this.$root.clearPurchaseKey("campaign:" + this.id);
      this.admission = null;
      this.pollTimeout = false;
      this.notice = "已生成新的参与请求，将重新申请一次名额。";
    },
    orderLink() {
      return this.admission && this.admission.orderId
        ? "#/orders/" + encodeURIComponent(this.admission.orderId)
        : "";
    },
  },
  template: `
    <div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="offer" class="detail-layout">
        <div class="card">
          <div class="meta-line"><status-badge :status="offer.state" :label="offer.state"></status-badge><span class="chip">{{ offer.applicationName }}</span></div>
          <h1 style="margin:10px 0 6px">{{ offer.productTitle }}</h1>
          <p class="muted">{{ offer.productSubtitle }}</p>
          <dl class="spec-list">
            <div><dt>价格</dt><dd>¥{{ (offer.price / 100).toFixed(2) }}</dd></div>
            <div><dt>次数 / 有效期</dt><dd>{{ offer.credits }} 次 / {{ offer.validDays }} 天</dd></div>
            <div><dt>剩余名额</dt><dd>{{ offer.remainingStock }}</dd></div>
            <div><dt>输出</dt><dd>{{ offer.outputCount }} 张 · {{ offer.imageSize }}</dd></div>
            <div><dt>开始时间</dt><dd>{{ $formatTime(offer.beginAt) }}</dd></div>
            <div><dt>结束时间</dt><dd>{{ $formatTime(offer.endAt) }}</dd></div>
          </dl>
          <h3>规则</h3>
          <p class="content-pre">{{ offer.productRules }}</p>
          <notice-box tone="warn" title="活动状态">{{ offer.displayNotice }}</notice-box>
        </div>
        <aside class="sticky-side">
          <div class="card">
            <h3 style="margin-top:0">参与活动</h3>
            <p class="muted">提交后先受理，订单由后台异步创建；请勿重复点击，重试会复用同一请求键。</p>
            <button class="button" type="button" :disabled="accepting || !offer.sellable" @click="admit">
              {{ accepting ? '提交中…' : '申请名额' }}
            </button>
            <button class="button ghost" type="button" style="margin-left:8px" @click="queryOnce">查询受理结果</button>
            <p v-if="notice" class="muted">{{ notice }}</p>
            <div v-if="admission" style="margin-top:16px">
              <div class="meta-line">
                <status-badge :status="admission.state" :label="admission.state"></status-badge>
                <span v-if="admission.existing" class="chip">重复请求返回原预约</span>
              </div>
              <p>{{ admission.acceptanceNotice }}</p>
              <p v-if="admission.reservationNotice" class="muted">{{ admission.reservationNotice }}</p>
              <p v-if="admission.consistencyNotice" class="muted">{{ admission.consistencyNotice }}</p>
              <p v-if="admission.entitlementNotice" class="muted">{{ admission.entitlementNotice }}</p>
              <p v-if="admission.state === 'PROCESSING' || admission.state === 'BOOKED'" class="muted">
                正在查询建单结果{{ polling ? '…' : '' }}（第 {{ pollAttempts }} 次）
              </p>
              <p v-if="pollTimeout" class="muted">尚未拿到最终结果；可稍后手动查询，页面不会无限轮询。</p>
              <a v-if="orderLink()" class="button" :href="orderLink()" style="margin-top:8px">查看订单</a>
              <button v-if="admission.state === 'FAILED'" class="button ghost" type="button" style="margin-top:8px" @click="retryNewIntent">以新的请求键重新参与</button>
            </div>
          </div>
          <notice-box tone="info" title="状态含义">已受理 ≠ 建单成功；建单成功 ≠ 已支付；已支付 ≠ 权益已到账；权益在完成后异步发放，到账与过期以真实查询为准。</notice-box>
        </aside>
      </div>
    </div>
  `,
};

const OrdersPage = {
  mixins: [pageCommon],
  data() {
    return { records: [], total: 0, page: 1, pageSize: 10 };
  },
  created() {
    if (!this.$root.user) {
      this.go(this.loginLink());
      return;
    }
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        const result = await this.$api("/trade/orders", { params: { page: this.page, size: this.pageSize } });
        this.records = result.data || [];
        this.total = Number(result.total || 0);
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    turn(delta) {
      const next = this.page + delta;
      if (next < 1 || (next - 1) * this.pageSize >= this.total) return;
      this.page = next;
      this.load();
    },
    orderLink(item) {
      return "#/orders/" + encodeURIComponent(item.id);
    },
  },
  computed: {
    totalPages() {
      return Math.max(1, Math.ceil(this.total / this.pageSize));
    },
  },
  template: `
    <div>
      <div class="section-head"><h2>我的订单</h2><span class="muted">共 {{ total }} 笔；订单 ID 按字符串处理</span></div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="!loading && !error" class="card">
        <div v-for="item in records" :key="item.id" class="order-row">
          <div>
            <div class="meta-line">
              <status-badge :status="item.status" :label="$orderStatus(item.status)"></status-badge>
              <span v-if="item.campaignId" class="chip">限量活动订单</span>
              <span class="muted">订单号 {{ item.id }}</span>
            </div>
            <h3 style="margin:6px 0 2px">{{ item.applicationName }} · {{ item.productTitle }}</h3>
            <div class="meta-line"><span>{{ item.credits }} 次 / {{ item.validDays }} 天</span><span>{{ $formatTime(item.createdAt) }}</span><span class="price">¥{{ (item.amount / 100).toFixed(2) }}</span></div>
          </div>
          <a class="button ghost small" :href="orderLink(item)">查看详情</a>
        </div>
        <empty-block v-if="!records.length" text="还没有订单，去应用页挑一个使用包吧"></empty-block>
      </div>
      <div v-if="!loading && totalPages > 1" class="actions" style="justify-content:center;margin-top:16px">
        <button class="button ghost small" type="button" :disabled="page <= 1" @click="turn(-1)">上一页</button>
        <span class="muted">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="button ghost small" type="button" :disabled="page >= totalPages" @click="turn(1)">下一页</button>
      </div>
    </div>
  `,
};

const OrderDetailPage = {
  mixins: [pageCommon],
  data() {
    return {
      id: this.$route.id,
      order: null,
      reservation: null,
      now: Date.now(),
      ticker: null,
      paying: false,
      cancelling: false,
      confirmingCancel: false,
      refreshing: false,
      expiryReloaded: false,
      pollTimer: null,
      polling: false,
      pollAttempts: 0,
      actionNotice: "",
      unmounted: false,
    };
  },
  created() {
    if (!this.$root.user) {
      this.go(this.loginLink());
      return;
    }
    this.load();
    this.ticker = window.setInterval(() => {
      this.now = Date.now();
      if (this.order && this.order.status === "UNPAID" && this.$isExpired(this.order.expireAt, this.now) && !this.expiryReloaded) {
        this.expiryReloaded = true;
        this.refresh();
      }
    }, 1000);
  },
  beforeUnmount() {
    this.unmounted = true;
    if (this.ticker) window.clearInterval(this.ticker);
    this.ticker = null;
    this.stopPolling();
  },
  computed: {
    remaining() {
      return this.order ? this.$countdown(this.order.expireAt, this.now) : "";
    },
    expired() {
      return this.order ? this.$isExpired(this.order.expireAt, this.now) : false;
    },
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        const result = await this.$api("/trade/orders/" + encodeURIComponent(this.id));
        this.order = result.data;
        if (this.order.campaignId) this.loadReservation();
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    async refresh() {
      this.refreshing = true;
      try {
        const result = await this.$api("/trade/orders/" + encodeURIComponent(this.id), { quiet: true });
        this.order = result.data;
      } catch (failure) {
        if (failure.status !== 401) this.fail(failure);
      } finally {
        this.refreshing = false;
      }
    },
    async loadReservation() {
      try {
        const result = await this.$api("/campaign/reservations/" + encodeURIComponent(this.id), { quiet: true });
        if (this.unmounted) return;
        this.reservation = result.data;
        if (["PROCESSING", "BOOKED"].indexOf(result.data.state) >= 0) {
          this.startReservationPolling();
        }
      } catch (failure) {
        if (failure.status === 401) {
          this.$root.notify("登录已过期，无法查询预约状态。", "error");
        }
      }
    },
    startReservationPolling() {
      if (this.unmounted || this.polling) return;
      this.polling = true;
      this.pollAttempts = 0;
      this.pollTimer = window.setTimeout(() => this.pollReservation(), 3000);
    },
    stopPolling() {
      this.polling = false;
      if (this.pollTimer) {
        window.clearTimeout(this.pollTimer);
        this.pollTimer = null;
      }
    },
    async pollReservation() {
      if (!this.polling) return;
      this.pollAttempts++;
      try {
        const result = await this.$api("/campaign/reservations/" + encodeURIComponent(this.id), { quiet: true });
        if (!this.polling) return;
        this.reservation = result.data;
        if (["PROCESSING", "BOOKED"].indexOf(result.data.state) < 0) {
          this.stopPolling();
          await this.refresh();
          return;
        }
      } catch (failure) {
        if (failure.status === 401) {
          this.stopPolling();
          return;
        }
      }
      // 请求返回前组件可能已卸载；此时不再续约定时器。
      if (!this.polling) return;
      if (this.pollAttempts >= 20) {
        this.stopPolling();
        return;
      }
      this.pollTimer = window.setTimeout(() => this.pollReservation(), 3000);
    },
    async pay() {
      if (!this.order || this.paying) return;
      this.paying = true;
      this.actionNotice = "";
      try {
        const result = await this.$api("/trade/orders/" + encodeURIComponent(this.id) + "/pay", {
          method: "POST",
          body: { expectedAmount: this.order.amount },
        });
        this.order = result.data;
        this.$root.notify(result.data.simulatedPayment ? "模拟支付成功，未发生真实扣款。" : "支付结果已确认。");
      } catch (failure) {
        this.actionNotice = failure.message;
        if (failure.status === 401) this.fail(failure);
        else this.$root.notify(failure.message, "error");
        await this.refresh();
      } finally {
        this.paying = false;
      }
    },
    async cancel() {
      if (!this.order || this.cancelling) return;
      this.cancelling = true;
      this.actionNotice = "";
      try {
        const result = await this.$api("/trade/orders/" + encodeURIComponent(this.id) + "/cancel", { method: "POST" });
        this.order = result.data;
        this.confirmingCancel = false;
        this.$root.notify("订单已关闭；如为限量订单，名额已释放。");
        if (this.reservation) await this.loadReservation();
      } catch (failure) {
        this.actionNotice = failure.message;
        this.$root.notify(failure.message, "error");
        await this.refresh();
      } finally {
        this.cancelling = false;
      }
    },
  },
  template: `
    <div>
      <div class="section-head"><h2>订单详情</h2><a href="#/orders">返回订单列表</a></div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="order" class="detail-layout">
        <div class="card">
          <div class="meta-line">
            <status-badge :status="order.status" :label="$orderStatus(order.status)"></status-badge>
            <span v-if="order.campaignId" class="chip">限量活动订单</span>
            <span v-if="order.simulatedPayment" class="chip">模拟支付，未真实扣款</span>
            <span class="muted">订单号 {{ order.id }}</span>
          </div>
          <h1 style="margin:10px 0 4px">{{ order.applicationName }} · {{ order.productTitle }}</h1>
          <p class="muted">{{ order.productSubtitle }}</p>
          <dl class="spec-list">
            <div><dt>金额</dt><dd>¥{{ (order.amount / 100).toFixed(2) }}</dd></div>
            <div><dt>资金渠道</dt><dd>{{ order.funding || '尚未支付' }}</dd></div>
            <div><dt>次数 / 有效期</dt><dd>{{ order.credits }} 次 / {{ order.validDays }} 天</dd></div>
            <div><dt>模型 / 尺寸</dt><dd>{{ order.modelVersion }} · {{ order.imageSize }}</dd></div>
            <div><dt>创建时间</dt><dd>{{ $formatTime(order.createdAt) }}</dd></div>
            <div><dt>支付截止</dt><dd>{{ $formatTime(order.expireAt) }}</dd></div>
            <div v-if="order.paidAt"><dt>支付时间</dt><dd>{{ $formatTime(order.paidAt) }}</dd></div>
            <div v-if="order.closedAt"><dt>关闭时间</dt><dd>{{ $formatTime(order.closedAt) }}</dd></div>
          </dl>
          <h3>商品规则</h3>
          <p class="content-pre">{{ order.productRules }}</p>
          <notice-box tone="info" title="订单状态">{{ order.entitlementNotice }}<span class="muted">{{ order.settlementNotice }}</span></notice-box>
          <notice-box v-if="actionNotice" tone="danger" title="本次操作未成功">{{ actionNotice }}</notice-box>

          <div v-if="order.status === 'UNPAID'" class="actions" style="margin-top:16px">
            <span class="muted">剩余支付时间 {{ remaining }}</span>
            <button class="button" type="button" :disabled="paying || expired" @click="pay">{{ paying ? '支付中…' : '模拟支付' }}</button>
            <button v-if="!confirmingCancel" class="button ghost small" type="button" :disabled="cancelling" @click="confirmingCancel = true">取消订单</button>
            <template v-else>
              <span class="muted">取消后不可恢复，限量订单会释放名额。</span>
              <button class="button danger small" type="button" :disabled="cancelling" @click="cancel">{{ cancelling ? '取消中…' : '确认取消' }}</button>
              <button class="button ghost small" type="button" @click="confirmingCancel = false">返回</button>
            </template>
          </div>
          <p v-if="order.status === 'UNPAID' && expired" class="muted">已超过支付截止时间，等待服务端关闭订单。</p>
          <div v-if="order.status === 'PAID'" style="margin-top:12px">
            <notice-box tone="success" title="已支付">订单已完成，未发生退款流程；权益在后台异步发放，是否到账、是否过期以上方真实状态为准；可在应用详情按固定规格提交任务并冻结次数，AI 执行、结算与结果交付尚未开放。</notice-box>
          </div>
          <div v-if="order.status === 'CANCELLED'" style="margin-top:12px">
            <notice-box tone="warn" title="已关闭">订单已关闭，未发生支付；如需再次购买请重新下单。</notice-box>
          </div>
          <div v-if="order.status === 'UNPAID' || refreshing" class="actions" style="margin-top:10px">
            <button class="button ghost small" type="button" :disabled="refreshing" @click="refresh">刷新服务端状态</button>
            <span class="muted">倒计时使用服务端截止时间，仅作展示；是否可支付以服务端判定为准。</span>
          </div>
        </div>

        <aside class="sticky-side">
          <div v-if="order.campaignId" class="card">
            <h3 style="margin-top:0">预约与建单进度</h3>
            <div v-if="reservation">
              <div class="meta-line"><status-badge :status="reservation.state" :label="$reservation(reservation.state)"></status-badge><span v-if="reservation.stockReleased" class="chip">名额已释放</span></div>
              <p>{{ reservation.acceptanceNotice }}</p>
              <p class="muted">{{ reservation.reservationNotice }}</p>
              <p v-if="reservation.consistencyNotice" class="muted">{{ reservation.consistencyNotice }}</p>
              <p class="muted">请求键 {{ reservation.requestKey }}</p>
              <p v-if="polling" class="muted">正在查询建单结果（{{ pollAttempts }}）…</p>
            </div>
            <empty-block v-else text="暂时无法读取预约状态"></empty-block>
          </div>
          <notice-box tone="warn" title="状态口径">
            已受理只表示后台收到请求；建单成功只表示订单已创建；已支付只表示订单成交，不等于权益已到账；权益在完成后异步发放，是否到账、是否过期以真实查询结果为准。
          </notice-box>
        </aside>
      </div>
    </div>
  `,
};

export {
  pageCommon,
  HomePage,
  AppsPage,
  AppDetailPage,
  CampaignsPage,
  CampaignDetailPage,
  OrdersPage,
  OrderDetailPage,
};

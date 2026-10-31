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

const LoginPage = {
  mixins: [pageCommon],
  data() {
    return {
      phone: "",
      code: "",
      demoCode: "",
      sending: false,
      loggingIn: false,
      returnTo: this.$route.query.returnTo || "#/home",
      phase: "code",
    };
  },
  methods: {
    async sendCode() {
      if (!/^1[3-9][0-9]{9}$/.test(this.phone)) {
        this.$root.notify("请输入有效的手机号。", "error");
        return;
      }
      this.sending = true;
      try {
        const result = await this.$api("/user/code", { method: "POST", params: { phone: this.phone } });
        this.demoCode = (result.data && result.data.demoCode) || "";
        this.phase = "login";
        this.$root.notify("演示验证码已生成，仅用于本地与测试环境。");
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.sending = false;
      }
    },
    async login() {
      if (!/^[0-9]{6}$/.test(this.code)) {
        this.$root.notify("请输入 6 位验证码。", "error");
        return;
      }
      this.loggingIn = true;
      try {
        const result = await this.$api("/user/login", {
          method: "POST",
          body: { phone: this.phone, code: this.code },
        });
        await this.$root.completeLogin(result.data);
        this.$root.notify("登录成功。");
        window.location.hash = this.returnTo || "#/home";
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loggingIn = false;
      }
    },
    useDemo() {
      this.code = this.demoCode;
    },
  },
  template: `
    <div class="login-card card">
      <h1>登录</h1>
      <p class="muted">演示环境使用本地验证码；生产环境未接入短信渠道时会明确返回不可用。</p>
      <div v-if="$root.user" class="notice notice-success">
        已登录：{{ $root.user.nickName }}（{{ $root.user.id }}）
        <button class="button ghost small" type="button" @click="$root.logout()">退出登录</button>
      </div>
      <template v-else>
        <label class="field" style="margin-bottom:12px">
          <span>手机号</span>
          <input v-model.trim="phone" maxlength="11" inputmode="numeric" placeholder="11 位手机号" />
        </label>
        <div class="actions" style="margin-bottom:12px">
          <button class="button" type="button" :disabled="sending" @click="sendCode">{{ sending ? '发送中…' : '获取验证码' }}</button>
          <span v-if="demoCode" class="muted">演示验证码：<strong class="demo-code">{{ demoCode }}</strong></span>
        </div>
        <label v-if="phase === 'login'" class="field" style="margin-bottom:12px">
          <span>验证码</span>
          <input v-model.trim="code" maxlength="6" inputmode="numeric" placeholder="6 位数字" @keyup.enter="login" />
        </label>
        <div v-if="phase === 'login'" class="actions">
          <button class="button" type="button" :disabled="loggingIn" @click="login">{{ loggingIn ? '登录中…' : '登录 / 注册' }}</button>
          <button v-if="demoCode" class="button ghost small" type="button" @click="useDemo">填入演示验证码</button>
        </div>
      </template>
      <error-block v-if="error" :error="error" action-label="关闭" @retry="error = null"></error-block>
      <p class="muted" style="margin-top:18px">登录会话默认 30 分钟空闲过期；过期后受保护操作会明确提示重新登录。</p>
    </div>
  `,
};

const MediaPage = {
  mixins: [pageCommon],
  data() {
    return { assets: [], quota: null, uploading: false, busyId: "", limit: 20, offset: 0 };
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
        const [assets, quota] = await Promise.all([
          this.$api("/media/mine", { params: { limit: this.limit, offset: this.offset } }),
          this.$api("/media/quota"),
        ]);
        this.assets = assets.data || [];
        this.quota = quota.data;
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    async upload(event) {
      const file = event.target.files && event.target.files[0];
      event.target.value = "";
      if (!file) return;
      if (file.size > (this.quota ? Number(this.quota.byteLimit) : 5000000)) {
        this.$root.notify("文件超过单个素材大小限制。", "error");
        return;
      }
      this.uploading = true;
      const form = new FormData();
      form.append("file", file);
      try {
        await this.$api("/media", { method: "POST", body: form });
        this.$root.notify("素材已上传为私有状态，只有发布作品时显式选中的素材才公开。");
        await this.load();
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.uploading = false;
      }
    },
    async remove(asset) {
      if (!window.confirm("删除后不可恢复；被未删除作品引用的素材会被服务端拒绝。确认删除？")) return;
      this.busyId = asset.id;
      try {
        await this.$api("/media/" + encodeURIComponent(asset.id), { method: "DELETE" });
        this.$root.notify("素材删除请求已完成。");
        await this.load();
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.busyId = "";
      }
    },
    pct(value, limit) {
      if (!limit) return 0;
      return Math.min(100, Math.round((Number(value) / Number(limit)) * 100));
    },
    sizeText(bytes) {
      const value = Number(bytes || 0);
      return value >= 1024 * 1024 ? (value / 1024 / 1024).toFixed(1) + " MB" : Math.ceil(value / 1024) + " KB";
    },
  },
  template: `
    <div>
      <div class="section-head"><h2>我的素材</h2><a href="#/create">用素材发布作品</a></div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <div class="card" style="margin-bottom:18px">
        <div class="form-row">
          <label class="button" style="cursor:pointer">
            {{ uploading ? '上传中…' : '上传素材' }}
            <input type="file" accept="image/*" style="display:none" :disabled="uploading" @change="upload" />
          </label>
          <span class="muted">图片默认私有；只有发布作品时显式选择的素材才会通过公开地址读取。</span>
        </div>
        <div v-if="quota" class="grid grid-2" style="margin-top:14px">
          <div>
            <div class="muted">容量 {{ sizeText(quota.usedBytes) }} / {{ sizeText(quota.byteLimit) }}</div>
            <div class="quota-bar"><span :style="{ width: pct(quota.usedBytes, quota.byteLimit) + '%' }"></span></div>
          </div>
          <div>
            <div class="muted">数量 {{ quota.assetCount }} / {{ quota.assetLimit }}</div>
            <div class="quota-bar"><span :style="{ width: pct(quota.assetCount, quota.assetLimit) + '%' }"></span></div>
          </div>
        </div>
      </div>
      <loading-block v-if="loading"></loading-block>
      <div v-if="!loading && assets.length" class="media-grid">
        <div v-for="asset in assets" :key="asset.id" class="media-tile">
          <app-image :src="asset.url" :alt="asset.originalName"></app-image>
          <div class="info">
            <span>{{ asset.originalName }}</span>
            <span class="muted">{{ asset.state }} · {{ sizeText(asset.sizeBytes) }}</span>
            <button class="button ghost small" type="button" :disabled="busyId === asset.id" @click="remove(asset)">
              {{ busyId === asset.id ? '删除中…' : '删除' }}
            </button>
          </div>
        </div>
      </div>
      <empty-block v-if="!loading && !assets.length" text="还没有上传素材"></empty-block>
    </div>
  `,
};

const CreatePage = {
  mixins: [pageCommon],
  data() {
    return {
      applications: [],
      assets: [],
      form: { applicationId: "", kind: "WORK", title: "", content: "", published: true, mediaIds: [] },
      uploading: false,
      submitting: false,
      result: null,
    };
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
        const [applications, assets] = await Promise.all([
          this.$api("/catalog/applications", { params: { sort: "newest", size: 20 } }),
          this.$api("/media/mine", { params: { limit: 50, offset: 0 } }),
        ]);
        this.applications = applications.data || [];
        this.assets = assets.data || [];
        if (this.applications.length) this.form.applicationId = this.applications[0].id;
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    async upload(event) {
      const file = event.target.files && event.target.files[0];
      event.target.value = "";
      if (!file) return;
      this.uploading = true;
      const form = new FormData();
      form.append("file", file);
      try {
        const result = await this.$api("/media", { method: "POST", body: form });
        this.assets.unshift(result.data);
        this.$root.notify("上传成功；勾选后才会随作品公开。");
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.uploading = false;
      }
    },
    toggleMedia(id) {
      const index = this.form.mediaIds.indexOf(id);
      if (index >= 0) this.form.mediaIds.splice(index, 1);
      else if (this.form.mediaIds.length >= 9) this.$root.notify("最多选择 9 个素材。", "error");
      else this.form.mediaIds.push(id);
    },
    selected(id) {
      return this.form.mediaIds.indexOf(id) >= 0;
    },
    async submit() {
      if (!this.form.applicationId || !this.form.title.trim() || !this.form.content.trim()) {
        this.$root.notify("请选择应用并填写标题与正文。", "error");
        return;
      }
      this.submitting = true;
      this.error = null;
      try {
        const result = await this.$api("/creations", {
          method: "POST",
          body: {
            applicationId: this.form.applicationId,
            kind: this.form.kind,
            title: this.form.title.trim(),
            content: this.form.content,
            mediaIds: this.form.mediaIds,
            published: this.form.published,
          },
        });
        this.result = result.data;
        this.$root.notify(this.form.published ? "作品已发布。" : "作品已保存为未发布。");
        if (this.form.published) this.go("#/creations/" + encodeURIComponent(result.data.id));
        else this.go("#/mine");
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.submitting = false;
      }
    },
  },
  template: `
    <div>
      <div class="section-head"><h2>发布作品或测评</h2><a href="#/media">管理素材</a></div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="!loading" class="card">
        <div class="form-grid">
          <label class="field">
            <span>应用</span>
            <select v-model="form.applicationId">
              <option v-for="app in applications" :key="app.id" :value="app.id">{{ app.name }}</option>
            </select>
          </label>
          <label class="field">
            <span>类型</span>
            <select v-model="form.kind">
              <option value="WORK">作品</option>
              <option value="REVIEW">测评</option>
            </select>
          </label>
        </div>
        <label class="field" style="margin-top:12px">
          <span>标题（最多 128 字符）</span>
          <input v-model.trim="form.title" maxlength="128" />
        </label>
        <label class="field" style="margin-top:12px">
          <span>正文（最多 20000 字符）</span>
          <textarea v-model="form.content" maxlength="20000"></textarea>
        </label>
        <div style="margin-top:12px">
          <div class="meta-line"><span>选择公开素材（{{ form.mediaIds.length }} / 9）</span>
            <label class="button ghost small" style="cursor:pointer">
              {{ uploading ? '上传中…' : '上传新素材' }}
              <input type="file" accept="image/*" style="display:none" :disabled="uploading" @change="upload" />
            </label>
          </div>
          <div class="media-grid" style="margin-top:10px">
            <button v-for="asset in assets" :key="asset.id" type="button" class="media-tile"
              :style="{ outline: selected(asset.id) ? '3px solid #4f46e5' : 'none' }" @click="toggleMedia(asset.id)">
              <app-image :src="asset.url" :alt="asset.originalName"></app-image>
              <div class="info"><span>{{ selected(asset.id) ? '已选择公开' : '仅私有' }}</span></div>
            </button>
          </div>
          <empty-block v-if="!assets.length" text="还没有素材，先上传一张图片"></empty-block>
        </div>
        <label class="actions" style="margin-top:14px">
          <input v-model="form.published" type="checkbox" />
          <span>立即发布（取消勾选则保存为未发布，仅自己可见）</span>
        </label>
        <notice-box tone="info" title="内容安全" style="margin-top:12px">
          正文按纯文本展示，不渲染执行任何 HTML 或脚本；发布前请确认素材来源与授权。
        </notice-box>
        <div class="actions" style="margin-top:16px">
          <button class="button" type="button" :disabled="submitting" @click="submit">{{ submitting ? '提交中…' : form.published ? '发布' : '保存草稿' }}</button>
          <span class="muted">提交失败会保留当前输入；请根据错误提示重试。</span>
        </div>
      </div>
    </div>
  `,
};

const CommunityPage = {
  mixins: [pageCommon],
  data() {
    return {
      tab: this.$route.query.tab || "hot",
      applicationId: this.$route.query.applicationId || "",
      records: [],
      total: 0,
      offset: 0,
      limit: 12,
      loadingMore: false,
      hasMore: false,
      nextTime: null,
      nextOffset: 0,
    };
  },
  created() {
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      this.offset = 0;
      this.records = [];
      try {
        if (this.tab === "follow") {
          if (!this.$root.user) {
            this.go(this.loginLink());
            return;
          }
          const result = await this.$api("/creations/feed", { params: { size: this.limit } });
          this.records = result.data.records || [];
          this.hasMore = result.data.hasMore;
          this.nextTime = result.data.nextTime;
          this.nextOffset = result.data.nextOffset;
        } else if (this.applicationId) {
          const result = await this.$api("/creations/by-application/" + encodeURIComponent(this.applicationId), {
            params: { limit: this.limit, offset: 0 },
          });
          this.records = result.data || [];
          this.total = Number(result.total || 0);
          this.hasMore = this.records.length < this.total;
        } else {
          const path = this.tab === "new" ? "/creations" : "/creations/hot";
          const result = await this.$api(path, { params: { limit: this.limit, offset: 0 } });
          this.records = result.data || [];
          this.total = Number(result.total || 0);
          this.hasMore = this.records.length < this.total;
        }
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    changeTab(tab) {
      this.tab = tab;
      this.load();
    },
    async more() {
      if (this.loadingMore) return;
      this.loadingMore = true;
      try {
        if (this.tab === "follow") {
          const result = await this.$api("/creations/feed", {
            params: { lastTime: this.nextTime, offset: this.nextOffset, size: this.limit },
          });
          this.records = this.records.concat(result.data.records || []);
          this.hasMore = result.data.hasMore;
          this.nextTime = result.data.nextTime;
          this.nextOffset = result.data.nextOffset;
        } else {
          this.offset += this.limit;
          let result;
          if (this.applicationId) {
            result = await this.$api("/creations/by-application/" + encodeURIComponent(this.applicationId), {
              params: { limit: this.limit, offset: this.offset },
            });
            this.records = this.records.concat(result.data || []);
          } else {
            const path = this.tab === "new" ? "/creations" : "/creations/hot";
            result = await this.$api(path, { params: { limit: this.limit, offset: this.offset } });
            this.records = this.records.concat(result.data || []);
          }
          this.total = Number(result.total || this.records.length);
          this.hasMore = this.records.length < this.total;
        }
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loadingMore = false;
      }
    },
  },
  template: `
    <div>
      <div class="section-head">
        <h2>作品社区</h2>
        <div class="tabs">
          <button class="tab" :class="{ active: tab === 'hot' }" type="button" @click="changeTab('hot')">热门</button>
          <button class="tab" :class="{ active: tab === 'new' }" type="button" @click="changeTab('new')">最新</button>
          <button class="tab" :class="{ active: tab === 'follow' }" type="button" @click="changeTab('follow')">关注</button>
          <a class="tab" href="#/create">发布</a>
        </div>
      </div>
      <p v-if="applicationId" class="muted">筛选应用 {{ applicationId }} · <a href="#/community">清除筛选</a></p>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="!loading" class="grid grid-2">
        <a v-for="item in records" :key="item.id" class="creation-card" :href="'#/creations/' + item.id">
          <app-image v-if="item.media && item.media.length" :src="item.media[0].url" :alt="item.title"></app-image>
          <div class="body">
            <div class="meta-line"><span class="chip">{{ item.kind === 'WORK' ? '作品' : '测评' }}</span><span>{{ item.applicationName }}</span></div>
            <h3>{{ item.title }}</h3>
            <p class="content">{{ item.content.slice(0, 120) }}{{ item.content.length > 120 ? '…' : '' }}</p>
            <div class="meta-line"><span>{{ item.authorNickName }}</span><span>♥ {{ item.likeCount }}</span><span>{{ $formatDate(item.publishedAt || item.createdAt) }}</span></div>
          </div>
        </a>
      </div>
      <empty-block v-if="!loading && !records.length" :text="tab === 'follow' ? '关注的人还没有发布作品' : '暂时没有作品'"></empty-block>
      <div v-if="!loading && hasMore" class="actions" style="justify-content:center;margin-top:18px">
        <button class="button ghost" type="button" :disabled="loadingMore" @click="more">{{ loadingMore ? '加载中…' : '加载更多' }}</button>
      </div>
    </div>
  `,
};

const CreationDetailPage = {
  mixins: [pageCommon],
  data() {
    return {
      id: this.$route.id,
      item: null,
      followState: null,
      busyLike: false,
      busyFollow: false,
      busyOwner: false,
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
        const result = await this.$api("/creations/" + encodeURIComponent(this.id));
        this.item = result.data;
        this.loadFollow();
      } catch (failure) {
        // 未发布作品对公开端点不可见；作者本人改从自己的详情端点读取真实状态。
        if (failure.status === 404 && this.$root.user) {
          try {
            const owned = await this.$api("/creations/mine/" + encodeURIComponent(this.id), { quiet: true });
            this.item = owned.data;
            this.loadFollow();
            return;
          } catch (ownedFailure) {
            this.fail(ownedFailure);
            return;
          }
        }
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    async loadFollow() {
      if (!this.$root.user || String(this.item.userId) === String(this.$root.user.id)) return;
      try {
        const result = await this.$api("/follows/" + encodeURIComponent(this.item.userId) + "/status", { quiet: true });
        this.followState = result.data;
      } catch {
        this.followState = null;
      }
    },
    async toggleLike() {
      if (!this.$root.user) {
        this.go(this.loginLink());
        return;
      }
      this.busyLike = true;
      try {
        const path = "/creations/" + encodeURIComponent(this.id) + "/likes/me";
        const result = await this.$api(path, { method: this.item.liked ? "DELETE" : "PUT" });
        this.item.liked = result.data.liked;
        this.item.likeCount = result.data.likeCount;
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.busyLike = false;
      }
    },
    async toggleFollow() {
      if (!this.$root.user) {
        this.go(this.loginLink());
        return;
      }
      this.busyFollow = true;
      try {
        const following = this.followState && this.followState.following;
        const result = await this.$api("/follows/" + encodeURIComponent(this.item.userId), {
          method: following ? "DELETE" : "PUT",
        });
        this.followState = result.data;
        this.$root.notify(following ? "已取消关注。" : "已关注。");
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.busyFollow = false;
      }
    },
    async setPublished(published) {
      this.busyOwner = true;
      try {
        const result = await this.$api("/creations/" + encodeURIComponent(this.id) + "/visibility", {
          method: "PUT",
          body: { published },
        });
        this.item = result.data;
        this.$root.notify(published ? "作品已发布。" : "作品已取消发布，公开链接不再读取媒体。");
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.busyOwner = false;
      }
    },
    async remove() {
      if (!window.confirm("删除后不可恢复，确认删除该作品？")) return;
      this.busyOwner = true;
      try {
        await this.$api("/creations/" + encodeURIComponent(this.id), { method: "DELETE" });
        this.$root.notify("作品已删除。");
        this.go("#/mine");
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.busyOwner = false;
      }
    },
    canManage() {
      return this.$root.user && this.item && String(this.item.userId) === String(this.$root.user.id);
    },
  },
  template: `
    <div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="item" class="detail-layout">
        <div class="card">
          <div class="meta-line">
            <span class="chip">{{ item.kind === 'WORK' ? '作品' : '测评' }}</span>
            <span>{{ item.applicationName }}</span>
            <status-badge :status="item.state" :label="item.state === 'PUBLISHED' ? '已发布' : '未发布'"></status-badge>
          </div>
          <h1 style="margin:10px 0 4px">{{ item.title }}</h1>
          <div class="meta-line">
            <span>{{ item.authorNickName }}</span>
            <span>{{ $formatTime(item.publishedAt || item.createdAt) }}</span>
            <span>♥ {{ item.likeCount }}</span>
          </div>
          <div v-if="item.media && item.media.length" class="media-grid" style="margin:16px 0">
            <div v-for="media in item.media" :key="media.mediaId" class="media-tile">
              <app-image :src="media.url" :alt="item.title"></app-image>
            </div>
          </div>
          <p class="content-pre">{{ item.content }}</p>
          <div class="actions" style="margin-top:18px">
            <button class="button" type="button" :disabled="busyLike" @click="toggleLike">
              {{ item.liked ? '取消点赞' : '点赞' }}（{{ item.likeCount }}）
            </button>
            <button v-if="!canManage()" class="button ghost" type="button" :disabled="busyFollow" @click="toggleFollow">
              {{ followState && followState.following ? '取消关注' : '关注作者' }}
            </button>
            <a v-if="!canManage()" class="button ghost" :href="'#/community?applicationId=' + item.applicationId">看同应用作品</a>
            <template v-if="canManage()">
              <button class="button ghost" type="button" :disabled="busyOwner" @click="setPublished(item.state !== 'PUBLISHED')">
                {{ item.state === 'PUBLISHED' ? '取消发布' : '发布' }}
              </button>
              <button class="button danger" type="button" :disabled="busyOwner" @click="remove">删除作品</button>
            </template>
          </div>
        </div>
        <aside class="sticky-side">
          <div class="card">
            <h3 style="margin-top:0">作者</h3>
            <div class="author-line"><span class="avatar">{{ (item.authorNickName || '?').slice(0, 1) }}</span><span>{{ item.authorNickName }}</span></div>
            <p v-if="followState" class="muted">{{ followState.followerCount }} 粉丝 · {{ followState.followingCount }} 关注</p>
            <a class="button ghost small" :href="'#/community?applicationId=' + item.applicationId">该应用其他作品</a>
          </div>
          <notice-box tone="info" title="版权与隐私">作品正文以纯文本展示；取消发布后，公开读取地址将不再返回素材。</notice-box>
        </aside>
      </div>
    </div>
  `,
};

const MinePage = {
  mixins: [pageCommon],
  data() {
    return {
      nickName: "",
      savingProfile: false,
      signStatus: null,
      signCount: 0,
      signing: false,
      creations: [],
      total: 0,
    };
  },
  created() {
    if (!this.$root.user) {
      this.go(this.loginLink());
      return;
    }
    this.nickName = this.$root.user.nickName || "";
    this.load();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        const [signStatus, signCount, creations] = await Promise.all([
          this.$api("/user/sign/status"),
          this.$api("/user/sign/count"),
          this.$api("/creations/mine", { params: { limit: 20, offset: 0 } }),
        ]);
        this.signStatus = signStatus.data;
        this.signCount = Number(signCount.data || 0);
        this.creations = creations.data || [];
        this.total = Number(creations.total || 0);
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    async saveProfile() {
      this.savingProfile = true;
      try {
        await this.$api("/user/me", { method: "PUT", body: { nickName: this.nickName } });
        await this.$root.loadSession();
        this.$root.notify("昵称已更新。");
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.savingProfile = false;
      }
    },
    async signIn() {
      this.signing = true;
      try {
        const result = await this.$api("/user/sign", { method: "POST" });
        // alreadySigned 表示“请求前是否已签到”；完成后本日状态一定为已签到。
        this.signStatus = Object.assign({}, result.data, { alreadySigned: true });
        this.signCount = result.data.consecutiveDays;
        this.$root.notify(result.data.alreadySigned ? "今天已经签到过。" : "签到完成（按 Asia/Shanghai 日期）。");
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.signing = false;
      }
    },
    async toggle(creation) {
      try {
        await this.$api("/creations/" + encodeURIComponent(creation.id) + "/visibility", {
          method: "PUT",
          body: { published: creation.state !== "PUBLISHED" },
        });
        await this.load();
      } catch (failure) {
        this.fail(failure);
      }
    },
  },
  template: `
    <div>
      <div class="section-head"><h2>个人中心</h2><a href="#/media">我的素材</a></div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <div class="grid grid-2">
        <div class="card">
          <h3 style="margin-top:0">资料</h3>
          <label class="field">
            <span>昵称（最多 32 字符）</span>
            <input v-model.trim="nickName" maxlength="32" />
          </label>
          <div class="actions" style="margin-top:12px">
            <button class="button" type="button" :disabled="savingProfile" @click="saveProfile">保存昵称</button>
            <a class="button ghost" href="#/orders">我的订单</a>
            <a class="button ghost" href="#/tasks">我的任务</a>
            <a class="button ghost" href="#/entitlements">我的权益</a>
          </div>
          <p class="muted">用户 ID {{ $root.user.id }} · 角色 {{ ($root.user.roles || []).join(', ') }}</p>
        </div>
        <div class="card">
          <h3 style="margin-top:0">每日签到</h3>
          <loading-block v-if="loading" text="读取签到状态…"></loading-block>
          <template v-else-if="signStatus">
            <p>今日状态：<strong>{{ signStatus.alreadySigned ? '已签到' : '未签到' }}</strong>（{{ signStatus.date }}，Asia/Shanghai）</p>
            <p class="muted">连续签到 {{ signCount }} 天</p>
            <button class="button" type="button" :disabled="signing || signStatus.alreadySigned" @click="signIn">
              {{ signStatus.alreadySigned ? '今天已签到' : signing ? '签到中…' : '签到' }}
            </button>
          </template>
        </div>
      </div>

      <section class="section">
        <div class="section-head"><h2>我的作品</h2><a href="#/create">发布新作品</a></div>
        <loading-block v-if="loading"></loading-block>
        <div v-if="!loading" class="grid grid-2">
          <div v-for="item in creations" :key="item.id" class="card">
            <div class="meta-line">
              <status-badge :status="item.state" :label="item.state === 'PUBLISHED' ? '已发布' : '未发布'"></status-badge>
              <span class="muted">{{ $formatTime(item.createdAt) }}</span>
            </div>
            <h3 style="margin:6px 0">{{ item.title }}</h3>
            <div class="actions">
              <a class="button ghost small" :href="'#/creations/' + item.id">查看</a>
              <button class="button ghost small" type="button" @click="toggle(item)">{{ item.state === 'PUBLISHED' ? '取消发布' : '发布' }}</button>
            </div>
          </div>
        </div>
        <empty-block v-if="!loading && !creations.length" text="还没有作品"></empty-block>
      </section>
    </div>
  `,
};

const NotFoundPage = {
  template: `
    <div class="state-block empty" role="alert">
      <div><strong>页面不存在</strong><p class="muted">请从导航重新进入。</p></div>
      <a class="button" href="#/home">返回首页</a>
    </div>
  `,
};

export {
  pageCommon,
  LoginPage,
  MediaPage,
  CreatePage,
  CommunityPage,
  CreationDetailPage,
  MinePage,
  NotFoundPage,
};

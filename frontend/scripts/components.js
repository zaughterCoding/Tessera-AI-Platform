const Vue = window.Vue;

// 私有素材必须带会话读取，公开素材只在后端确认已发布后可直连。
Vue.component("app-image", {
  props: {
    src: { type: String, default: "" },
    alt: { type: String, default: "" },
    ratio: { type: String, default: "4 / 3" },
  },
  data() {
    return { blobUrl: "", failed: false };
  },
  computed: {
    directUrl() {
      if (!this.src) return "";
      if (this.src.indexOf("/media/public/") === 0) return "/api" + this.src;
      if (this.src.indexOf("/media/mine/") === 0) return "";
      if (this.src.indexOf("http") === 0 || this.src.indexOf("/api/") === 0) {
        return this.src;
      }
      return this.src;
    },
  },
  watch: {
    src: {
      immediate: true,
      handler() {
        this.load();
      },
    },
  },
  methods: {
    async load() {
      this.release();
      this.failed = false;
      if (!this.src || this.directUrl) return;
      if (this.src.indexOf("/media/mine/") !== 0) {
        this.failed = true;
        return;
      }
      try {
        const response = await fetch("/api" + this.src, {
          headers: this.$authorizedHeaders(),
        });
        if (!response.ok) throw new Error("media");
        this.blobUrl = URL.createObjectURL(await response.blob());
      } catch {
        this.failed = true;
      }
    },
    release() {
      if (this.blobUrl) {
        URL.revokeObjectURL(this.blobUrl);
        this.blobUrl = "";
      }
    },
  },
  beforeDestroy() {
    this.release();
  },
  template: `
    <span class="app-image" :style="{ aspectRatio: ratio }">
      <img v-if="blobUrl || directUrl" :src="blobUrl || directUrl" :alt="alt" loading="lazy" @error="failed = true" />
      <span v-else class="image-placeholder">{{ failed ? '图片不可用' : '加载中' }}</span>
    </span>
  `,
});

Vue.component("notice-box", {
  props: {
    tone: { type: String, default: "info" },
    title: { type: String, default: "" },
  },
  template: `
    <div class="notice" :class="'notice-' + tone">
      <strong v-if="title">{{ title }}</strong>
      <slot></slot>
    </div>
  `,
});

Vue.component("loading-block", {
  props: { text: { type: String, default: "正在加载真实数据…" } },
  template: `<div class="state-block" role="status"><span class="spinner"></span><span>{{ text }}</span></div>`,
});

Vue.component("empty-block", {
  props: { text: { type: String, default: "暂时没有数据" } },
  template: `<div class="state-block empty" role="status">{{ text }}</div>`,
});

Vue.component("error-block", {
  props: { error: { type: Object, default: null }, actionLabel: { type: String, default: "重试" } },
  template: `
    <div class="state-block error" role="alert">
      <div>
        <strong>{{ error ? error.message : '请求未完成' }}</strong>
        <p v-if="error && error.code" class="muted">
          {{ error.code }}<span v-if="error.requestId"> · 请求编号 {{ error.requestId }}</span>
        </p>
      </div>
      <button class="button ghost" type="button" @click="$emit('retry')">{{ actionLabel }}</button>
    </div>
  `,
});

Vue.component("status-badge", {
  props: { status: { type: String, default: "" }, label: { type: String, default: "" } },
  template: `<span class="badge" :class="'badge-' + (status || 'unknown').toLowerCase()">{{ label || status }}</span>`,
});

Vue.component("admin-tabs", {
  props: { current: { type: String, default: "" } },
  data() {
    return {
      tabs: [
        { key: "campaigns", label: "限量活动" },
        { key: "messaging", label: "可靠任务" },
        { key: "analytics", label: "行为分析" },
        { key: "operations", label: "审计与保留" },
        { key: "experience", label: "技术体验" },
      ],
    };
  },
  template: `
    <nav class="admin-tabs" aria-label="运营台分区">
      <button v-for="tab in tabs" :key="tab.key" type="button" class="tab"
        :class="{ active: current === tab.key }" @click="$emit('select', tab.key)">{{ tab.label }}</button>
    </nav>
  `,
});

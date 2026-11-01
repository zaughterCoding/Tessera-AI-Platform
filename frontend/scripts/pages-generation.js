import { pageCommon } from "./pages-more.js";

// 只展示服务端记录的调度、交付与结算事实；下载与取消都走服务端登记的私有入口。
const TASK_STATE_LABELS = {
  QUEUED: "排队中",
  DISPATCHING: "调度中",
  SUBMITTED: "上游已受理",
  RUNNING: "上游执行中",
  RECONCILING: "对账中",
  RESULT_PENDING: "待交付",
  FAILED_PENDING_FOLLOWUP: "上游失败·待处理",
  CANCELLED_PENDING_FOLLOWUP: "上游取消·待处理",
  QUEUE_TIMEOUT: "排队超时·待处理",
  REVIEW: "人工核对",
  CANCEL_REQUESTED: "取消确认中",
  SUCCEEDED: "已完成",
  FAILED: "已失败",
  CANCELLED: "已取消",
};

const STATE_BADGES = {
  QUEUED: "processing",
  DISPATCHING: "processing",
  SUBMITTED: "processing",
  RUNNING: "processing",
  RECONCILING: "processing",
  RESULT_PENDING: "success",
  FAILED_PENDING_FOLLOWUP: "ended",
  CANCELLED_PENDING_FOLLOWUP: "ended",
  QUEUE_TIMEOUT: "ended",
  REVIEW: "review",
  CANCEL_REQUESTED: "processing",
  SUCCEEDED: "success",
  FAILED: "ended",
  CANCELLED: "ended",
};

const RESULT_STATE_LABELS = {
  PENDING: "待保存",
  SAVING: "保存中",
  SAVED: "已保存",
  DELIVERED: "已交付",
  FAILED: "保存失败",
};

const CANCEL_STATE_LABELS = {
  PENDING: "取消确认中",
  RETRYING: "取消确认中·重试",
  RESOLVED: "取消已生效",
  REJECTED: "未取消",
  UNSUPPORTED: "渠道不支持取消",
};

const ACCOUNTING_LABELS = {
  FROZEN: "次数已冻结",
  CONSUMED: "次数已消费",
  RELEASED: "次数已释放",
};

const LEDGER_TYPE_LABELS = {
  GRANT: "发放",
  EXPIRE: "过期",
  CORRECTION: "更正",
  FREEZE: "冻结",
};

function taskStateLabel(state) {
  return TASK_STATE_LABELS[state] || state || "未知";
}

function stateBadge(state) {
  return STATE_BADGES[state] || "unknown";
}

function accountingLabel(state) {
  return ACCOUNTING_LABELS[state] || state || "未知";
}

function ledgerTypeLabel(type) {
  return LEDGER_TYPE_LABELS[type] || type || "未知";
}

function resultStateLabel(state) {
  return RESULT_STATE_LABELS[state] || state || "未知";
}

function cancelStateLabel(state) {
  return CANCEL_STATE_LABELS[state] || state || "未知";
}

function resultExtension(contentType) {
  if (contentType === "image/jpeg") return ".jpg";
  if (contentType === "image/webp") return ".webp";
  return ".png";
}

const TasksPage = {
  mixins: [pageCommon],
  data() {
    return { records: [], total: 0, page: 1, pageSize: 10, submitStatus: null };
  },
  created() {
    if (!this.$root.user) {
      this.go(this.loginLink());
      return;
    }
    this.load();
    this.loadStatus();
  },
  methods: {
    async load() {
      this.loading = true;
      this.error = null;
      try {
        const result = await this.$api("/generation/tasks", {
          params: { page: this.page, size: this.pageSize },
        });
        this.records = result.data || [];
        this.total = Number(result.total || 0);
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    async loadStatus() {
      try {
        const result = await this.$api("/generation/status");
        this.submitStatus = result.data;
      } catch {
        this.submitStatus = null;
      }
    },
    turn(delta) {
      const next = this.page + delta;
      if (next < 1 || (next - 1) * this.pageSize >= this.total) return;
      this.page = next;
      this.load();
    },
    taskLink(item) {
      return "#/tasks/" + encodeURIComponent(item.id);
    },
    stateLabel: taskStateLabel,
    stateBadge,
    accountingLabel,
  },
  computed: {
    totalPages() {
      return Math.max(1, Math.ceil(this.total / this.pageSize));
    },
  },
  template: `
    <div>
      <div class="section-head">
        <h2>我的任务</h2>
        <span class="muted">共 {{ total }} 个；本页不自动轮询，可刷新查看进展</span>
      </div>
      <notice-box v-if="submitStatus && !submitStatus.submitEnabled" tone="warn" title="提交当前未开启">
        {{ submitStatus.notice }}已提交任务的次数冻结与查询不受影响。
      </notice-box>
      <notice-box v-else-if="submitStatus && !submitStatus.schedulingEnabled" tone="warn" title="调度当前暂停">
        {{ submitStatus.notice }}
      </notice-box>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="!loading && !error" class="card">
        <div v-for="item in records" :key="item.id" class="order-row">
          <div>
            <div class="meta-line">
              <status-badge :status="stateBadge(item.state)" :label="stateLabel(item.state)"></status-badge>
              <status-badge status="frozen" :label="accountingLabel(item.accountingState)"></status-badge>
              <span v-if="item.channelDisplayName" class="chip">{{ item.simulatedChannel ? '模拟渠道' : '真实渠道' }}</span>
              <span class="muted">任务号 {{ item.id }}</span>
            </div>
            <h3 style="margin:6px 0 2px">{{ item.applicationName }} · 版本 {{ item.applicationVersion }}</h3>
            <div class="meta-line">
              <span>{{ item.modelVersion }} / {{ item.imageSize }} / {{ item.outputCount }} 张</span>
              <span>冻结 {{ item.frozenUnits }} 次</span>
              <span>结果 {{ (item.results || []).length }} / {{ item.outputCount }}</span>
              <span>{{ $formatTime(item.createdAt) }}</span>
            </div>
            <div class="muted">{{ item.executionNotice }}</div>
          </div>
          <a class="button ghost small" :href="taskLink(item)">查看详情</a>
        </div>
        <empty-block v-if="!records.length" text="还没有任务；可在应用页用已到账权益提交固定规格请求"></empty-block>
      </div>
      <div v-if="!loading && totalPages > 1" class="actions" style="justify-content:center;margin-top:16px">
        <button class="button ghost small" type="button" :disabled="page <= 1" @click="turn(-1)">上一页</button>
        <span class="muted">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="button ghost small" type="button" :disabled="page >= totalPages" @click="turn(1)">下一页</button>
      </div>
    </div>
  `,
};

const TaskDetailPage = {
  mixins: [pageCommon],
  data() {
    return {
      id: this.$route.id,
      task: null,
      cancelReason: "",
      canceling: false,
      downloadingId: null,
      share: { selected: [], title: "", content: "", published: false },
      sharing: false,
      sharedCreation: null,
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
        const result = await this.$api("/generation/tasks/" + encodeURIComponent(this.id));
        this.task = result.data;
      } catch (failure) {
        this.fail(failure);
      } finally {
        this.loading = false;
      }
    },
    grantLink() {
      return "#/entitlements/" + encodeURIComponent(this.task.grantId);
    },
    async downloadResult(result) {
      if (!result.downloadAvailable) return;
      this.downloadingId = result.id;
      try {
        const response = await fetch(
          "/api/generation/tasks/" + encodeURIComponent(this.task.id)
            + "/results/" + encodeURIComponent(result.id) + "/content",
          { headers: this.$authorizedHeaders(), credentials: "same-origin" },
        );
        if (!response.ok) throw new Error("结果下载失败");
        const objectUrl = URL.createObjectURL(await response.blob());
        const link = document.createElement("a");
        link.href = objectUrl;
        link.download = "generation-" + this.task.id + "-" + result.position
          + resultExtension(result.contentType);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.requestAnimationFrame(() => URL.revokeObjectURL(objectUrl));
      } catch (failure) {
        this.$root.notify((failure && failure.message) || "结果下载失败", "error");
      } finally {
        this.downloadingId = null;
      }
    },
    shareableResults() {
      return (this.task && this.task.results ? this.task.results : []).filter(
        (result) => result.downloadAvailable,
      );
    },
    toggleShare(resultId) {
      const index = this.share.selected.indexOf(resultId);
      if (index >= 0) {
        this.share.selected.splice(index, 1);
        return;
      }
      if (this.share.selected.length >= 9) {
        this.$root.notify("一次最多分享 9 个结果。", "error");
        return;
      }
      this.share.selected.push(resultId);
    },
    // 分享只提交用户显式勾选的结果；是否公开由用户再次确认，默认保存为私有作品。
    async shareResults() {
      if (!this.share.selected.length) {
        this.$root.notify("请先勾选要分享的结果。", "error");
        return;
      }
      if (!(this.share.title || "").trim() || !(this.share.content || "").trim()) {
        this.$root.notify("请填写作品标题和介绍。", "error");
        return;
      }
      if (this.share.published
        && !window.confirm("公开后其他访客可以看到这些结果图片，确认公开分享？")) {
        return;
      }
      this.sharing = true;
      try {
        const result = await this.$api(
          "/generation/tasks/" + encodeURIComponent(this.task.id) + "/share",
          {
            method: "POST",
            body: {
              resultIds: this.share.selected.slice(),
              title: this.share.title.trim(),
              content: this.share.content.trim(),
              published: Boolean(this.share.published),
            },
          },
        );
        this.sharedCreation = result.data;
        this.share = { selected: [], title: "", content: "", published: false };
        this.$root.notify("已创建作品；未公开时仅本人可见。");
      } catch (failure) {
        this.$root.notify((failure && failure.message) || "分享未完成", "error");
      } finally {
        this.sharing = false;
      }
    },
    async cancelTask() {
      this.canceling = true;
      try {
        const result = await this.$api(
          "/generation/tasks/" + encodeURIComponent(this.task.id) + "/cancel",
          { method: "POST", body: { reason: (this.cancelReason || "").trim() } },
        );
        this.task = result.data;
        this.$root.notify("取消请求已登记；是否生效以上游确认为准。");
      } catch (failure) {
        this.$root.notify((failure && failure.message) || "取消请求未登记", "error");
      } finally {
        this.canceling = false;
      }
    },
    stateLabel: taskStateLabel,
    stateBadge,
    accountingLabel,
    resultStateLabel,
    cancelStateLabel,
  },
  template: `
    <div>
      <div class="section-head"><h2>任务详情</h2><a href="#/tasks">返回任务列表</a></div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="task" class="grid grid-2">
        <div class="card">
          <div class="meta-line">
            <status-badge :status="stateBadge(task.state)" :label="stateLabel(task.state)"></status-badge>
            <status-badge status="frozen" :label="accountingLabel(task.accountingState)"></status-badge>
            <span v-if="task.channelDisplayName" class="chip">{{ task.simulatedChannel ? '模拟渠道' : '真实渠道' }}</span>
            <span class="muted">任务号 {{ task.id }}</span>
          </div>
          <h3 style="margin:10px 0 4px">{{ task.applicationName }}</h3>
          <dl class="kv">
            <dt>应用版本</dt><dd>{{ task.applicationVersion }}</dd>
            <dt>模型版本</dt><dd>{{ task.modelVersion }}</dd>
            <dt>输出规格</dt><dd>{{ task.imageSize }} · {{ task.outputCount }} 张</dd>
            <dt>执行渠道</dt><dd>{{ task.channelDisplayName || '调度中' }}</dd>
            <dt>上游尝试</dt><dd>{{ task.attemptState || '尚未创建' }}</dd>
            <dt>冻结次数</dt><dd>{{ task.frozenUnits }}</dd>
            <dt>提交时间</dt><dd>{{ $formatTime(task.createdAt) }}</dd>
            <dt v-if="task.deliveredAt">交付时间</dt><dd v-if="task.deliveredAt">{{ $formatTime(task.deliveredAt) }}</dd>
            <dt v-if="task.settledAt">结算时间</dt><dd v-if="task.settledAt">{{ $formatTime(task.settledAt) }}</dd>
          </dl>
          <notice-box tone="info" title="执行状态">{{ task.executionNotice }}</notice-box>
        </div>
        <div class="card">
          <h3 style="margin-top:0">冻结来源</h3>
          <dl class="kv">
            <dt>权益包</dt>
            <dd><a :href="grantLink()">{{ task.grantId }}</a></dd>
            <dt>权益状态</dt>
            <dd>{{ task.grantStatus === 'EXPIRED' ? '已过期（不影响已冻结次数）' : '有效' }}</dd>
            <dt>有效期至</dt><dd>{{ $formatTime(task.grantExpiresAt) }}</dd>
            <dt>素材数量</dt><dd>{{ (task.mediaIds || []).length }}</dd>
          </dl>
          <h3>提示词</h3>
          <p class="content-pre">{{ task.prompt }}</p>
          <template v-if="task.negativePrompt">
            <h3>排除内容</h3>
            <p class="content-pre">{{ task.negativePrompt }}</p>
          </template>
          <p class="muted">任务与冻结在提交事务中一起落库；重试提交只会返回本任务，不会再次冻结。</p>
        </div>

        <div v-if="task.results && task.results.length" class="card" style="grid-column:1 / -1">
          <h3 style="margin-top:0">结果文件</h3>
          <p class="muted">只有服务端保存并校验通过的私有文件可以下载；上游地址不会出现在页面中。</p>
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>序号</th><th>状态</th><th>类型</th><th>大小</th><th>摘要</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="result in task.results" :key="result.id">
                  <td>{{ result.position }}</td>
                  <td>{{ resultStateLabel(result.state) }}</td>
                  <td>{{ result.contentType || '—' }}</td>
                  <td>{{ result.sizeBytes == null ? '—' : result.sizeBytes + ' B' }}</td>
                  <td class="muted">{{ result.contentDigest || '—' }}</td>
                  <td>
                    <button v-if="result.downloadAvailable" class="button ghost small" type="button"
                      :disabled="downloadingId === result.id" @click="downloadResult(result)">
                      {{ downloadingId === result.id ? '下载中…' : '下载' }}
                    </button>
                    <span v-else class="muted">暂不可下载</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-if="shareableResults().length" class="card" style="grid-column:1 / -1">
          <h3 style="margin-top:0">分享到作品</h3>
          <p class="muted">只分享你勾选的结果；默认保存为私有作品，公开必须由你再次确认。未公开的作品不会被他人看到。</p>
          <div class="actions" style="flex-wrap:wrap;gap:8px">
            <label v-for="result in shareableResults()" :key="result.id" class="chip">
              <input type="checkbox" :checked="share.selected.includes(result.id)"
                @change="toggleShare(result.id)" />
              第 {{ result.position }} 张
            </label>
          </div>
          <label class="field"><span>作品标题</span>
            <input v-model="share.title" maxlength="128" placeholder="给这组作品起个名字" />
          </label>
          <label class="field"><span>作品介绍</span>
            <textarea v-model="share.content" maxlength="20000" rows="3" placeholder="介绍创作思路（公开后访客可见）"></textarea>
          </label>
          <label class="chip" style="display:inline-flex;gap:6px;align-items:center">
            <input v-model="share.published" type="checkbox" />
            同时公开分享（访客可见）
          </label>
          <div class="actions">
            <button class="button" type="button" :disabled="sharing" @click="shareResults">
              {{ sharing ? '提交中…' : '创建作品' }}
            </button>
          </div>
          <p v-if="sharedCreation" class="muted">
            已创建作品 <a :href="'#/creations/' + encodeURIComponent(sharedCreation.id)">#{{ sharedCreation.id }}</a>，
            当前状态：{{ sharedCreation.state === 'PUBLISHED' ? '已公开' : '私有' }}。
            可在<a href="#/mine">我的</a>页管理作品与公开状态。
          </p>
        </div>

        <div v-if="task.canCancel" class="card" style="grid-column:1 / -1">
          <h3 style="margin-top:0">取消任务</h3>
          <p class="muted">取消是持久请求：排队任务确认未提交后立即结束；执行中任务需等待上游确认，确认前冻结次数保留。</p>
          <label class="field"><span>取消原因（可选）</span>
            <input v-model="cancelReason" maxlength="200" placeholder="简要说明取消原因" />
          </label>
          <div class="actions">
            <button class="button danger" type="button" :disabled="canceling" @click="cancelTask">
              {{ canceling ? '提交中…' : '请求取消' }}
            </button>
          </div>
        </div>

        <div v-if="task.cancelRequest" class="card" style="grid-column:1 / -1">
          <h3 style="margin-top:0">取消记录</h3>
          <dl class="kv">
            <dt>处理状态</dt><dd>{{ cancelStateLabel(task.cancelRequest.state) }}</dd>
            <dt>结果</dt><dd>{{ task.cancelRequest.outcome || '等待后台确认' }}</dd>
            <dt>请求时间</dt><dd>{{ $formatTime(task.cancelRequest.requestedAt) }}</dd>
            <dt>完成时间</dt><dd>{{ $formatTime(task.cancelRequest.resolvedAt) }}</dd>
          </dl>
        </div>

        <div v-if="task.settlement" class="card" style="grid-column:1 / -1">
          <h3 style="margin-top:0">结算说明</h3>
          <div>{{ task.settlement.description }}</div>
          <p class="muted">结算只发生一次；消费或释放都由权益流水记录，已消费次数不会退回。</p>
        </div>
      </div>
    </div>
  `,
};

const EntitlementsPage = {
  mixins: [pageCommon],
  data() {
    return { balance: null, grants: [], total: 0, page: 1, pageSize: 10 };
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
        const [balance, grants] = await Promise.all([
          this.$api("/entitlement/balance"),
          this.$api("/entitlement/grants", { params: { page: this.page, size: this.pageSize } }),
        ]);
        this.balance = balance.data;
        this.grants = grants.data || [];
        this.total = Number(grants.total || 0);
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
    grantLink(item) {
      return "#/entitlements/" + encodeURIComponent(item.id);
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
        <h2>我的权益</h2>
        <a href="#/tasks">查看我的任务</a>
      </div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <template v-if="!loading && !error">
        <div v-if="balance" class="grid grid-2">
          <div class="card">
            <h3 style="margin-top:0">汇总余额</h3>
            <dl class="kv">
              <dt>累计发放</dt><dd>{{ balance.totalCredits }}</dd>
              <dt>可用</dt><dd><strong>{{ balance.availableCredits }}</strong></dd>
              <dt>冻结</dt><dd>{{ balance.frozenCredits }}</dd>
              <dt>已消费</dt><dd>{{ balance.consumedCredits }}</dd>
              <dt>已过期</dt><dd>{{ balance.expiredCredits }}</dd>
              <dt>有效包 / 过期包</dt><dd>{{ balance.activeGrants }} / {{ balance.expiredGrants }}</dd>
            </dl>
          </div>
          <div class="card">
            <h3 style="margin-top:0">余额口径</h3>
            <p class="muted">总次数 = 可用 + 冻结 + 已消费 + 已过期。冻结表示任务已受理但尚未结算；上游执行与对账阶段不会提前消费或释放冻结次数。</p>
            <p class="muted">到期扫描只回收可用次数，不会改动已冻结次数；是否过期以真实查询结果为准。</p>
          </div>
        </div>

        <section class="section">
          <div class="section-head"><h2>权益包</h2><span class="muted">共 {{ total }} 个</span></div>
          <div class="card">
            <div v-for="item in grants" :key="item.id" class="order-row">
              <div>
                <div class="meta-line">
                  <status-badge :status="item.status" :label="item.status === 'ACTIVE' ? '有效' : '已过期'"></status-badge>
                  <span class="muted">权益包 {{ item.id }}</span>
                </div>
                <h3 style="margin:6px 0 2px">{{ item.applicationName }} · 版本 {{ item.applicationVersion }}</h3>
                <div class="meta-line">
                  <span>{{ item.modelVersion }} / {{ item.imageSize }} / {{ item.outputCount }} 张</span>
                  <span>可用 {{ item.availableCredits }} · 冻结 {{ item.frozenCredits }}</span>
                  <span>有效期至 {{ $formatTime(item.expiresAt) }}</span>
                </div>
              </div>
              <a class="button ghost small" :href="grantLink(item)">查看流水</a>
            </div>
            <empty-block v-if="!grants.length" text="还没有权益包；购买或领取成功后异步发放"></empty-block>
          </div>
          <div v-if="totalPages > 1" class="actions" style="justify-content:center;margin-top:16px">
            <button class="button ghost small" type="button" :disabled="page <= 1" @click="turn(-1)">上一页</button>
            <span class="muted">第 {{ page }} / {{ totalPages }} 页</span>
            <button class="button ghost small" type="button" :disabled="page >= totalPages" @click="turn(1)">下一页</button>
          </div>
        </section>
      </template>
    </div>
  `,
};

const EntitlementDetailPage = {
  mixins: [pageCommon],
  data() {
    return {
      id: this.$route.id,
      grant: null,
      ledgers: [],
      total: 0,
      page: 1,
      pageSize: 20,
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
        const [grant, ledgers] = await Promise.all([
          this.$api("/entitlement/grants/" + encodeURIComponent(this.id)),
          this.$api("/entitlement/grants/" + encodeURIComponent(this.id) + "/ledger", {
            params: { page: this.page, size: this.pageSize },
          }),
        ]);
        this.grant = grant.data;
        this.ledgers = ledgers.data || [];
        this.total = Number(ledgers.total || 0);
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
    ledgerTypeLabel,
    hasDelta(value) {
      return value !== 0 && value != null;
    },
    signed(value) {
      if (value == null || value === 0) return "0";
      return value > 0 ? "+" + value : String(value);
    },
    referenceLink(item) {
      if (item.referenceType !== "TASK" || item.referenceId == null) return "";
      return "#/tasks/" + encodeURIComponent(item.referenceId);
    },
  },
  computed: {
    totalPages() {
      return Math.max(1, Math.ceil(this.total / this.pageSize));
    },
  },
  template: `
    <div>
      <div class="section-head"><h2>权益流水</h2><a href="#/entitlements">返回权益列表</a></div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <template v-if="grant && !loading">
        <div class="grid grid-2">
          <div class="card">
            <div class="meta-line">
              <status-badge :status="grant.status" :label="grant.status === 'ACTIVE' ? '有效' : '已过期'"></status-badge>
              <span class="muted">权益包 {{ grant.id }}</span>
            </div>
            <h3 style="margin:10px 0 4px">{{ grant.applicationName }}</h3>
            <dl class="kv">
              <dt>应用版本</dt><dd>{{ grant.applicationVersion }}</dd>
              <dt>固定规格</dt><dd>{{ grant.modelVersion }} / {{ grant.imageSize }} / {{ grant.outputCount }} 张</dd>
              <dt>生效 / 到期</dt><dd>{{ $formatTime(grant.effectiveFrom) }} · {{ $formatTime(grant.expiresAt) }}</dd>
            </dl>
          </div>
          <div class="card">
            <h3 style="margin-top:0">当前余额</h3>
            <dl class="kv">
              <dt>总次数</dt><dd>{{ grant.totalCredits }}</dd>
              <dt>可用</dt><dd><strong>{{ grant.availableCredits }}</strong></dd>
              <dt>冻结</dt><dd>{{ grant.frozenCredits }}</dd>
              <dt>已消费</dt><dd>{{ grant.consumedCredits }}</dd>
              <dt>已过期</dt><dd>{{ grant.expiredCredits }}</dd>
            </dl>
            <p class="muted">流水只追加不修改；冻结记录关联任务，重试提交不会生成第二条冻结。</p>
          </div>
        </div>

        <section class="section">
          <div class="section-head"><h2>操作流水</h2><span class="muted">共 {{ total }} 条</span></div>
          <div class="card">
            <div v-for="item in ledgers" :key="item.id" class="order-row">
              <div>
                <div class="meta-line">
                  <span class="chip">{{ ledgerTypeLabel(item.operationType) }}</span>
                  <span class="muted">{{ $formatTime(item.createdAt) }}</span>
                </div>
                <div style="margin-top:6px">{{ item.reason }}</div>
                <div class="meta-line" style="margin-top:6px">
                  <span v-if="hasDelta(item.deltaTotal)">总次数 {{ signed(item.deltaTotal) }}</span>
                  <span v-if="hasDelta(item.deltaAvailable)">可用 {{ signed(item.deltaAvailable) }}</span>
                  <span v-if="hasDelta(item.deltaFrozen)">冻结 {{ signed(item.deltaFrozen) }}</span>
                  <span v-if="hasDelta(item.deltaConsumed)">消费 {{ signed(item.deltaConsumed) }}</span>
                  <span v-if="hasDelta(item.deltaExpired)">过期 {{ signed(item.deltaExpired) }}</span>
                </div>
                <div class="muted">
                  操作后余额：总 {{ item.totalAfter }} / 可用 {{ item.availableAfter }} / 冻结 {{ item.frozenAfter }} / 消费 {{ item.consumedAfter }} / 过期 {{ item.expiredAfter }}
                </div>
              </div>
              <a v-if="referenceLink(item)" class="button ghost small" :href="referenceLink(item)">查看任务</a>
            </div>
            <empty-block v-if="!ledgers.length" text="暂无流水"></empty-block>
          </div>
          <div v-if="totalPages > 1" class="actions" style="justify-content:center;margin-top:16px">
            <button class="button ghost small" type="button" :disabled="page <= 1" @click="turn(-1)">上一页</button>
            <span class="muted">第 {{ page }} / {{ totalPages }} 页</span>
            <button class="button ghost small" type="button" :disabled="page >= totalPages" @click="turn(1)">下一页</button>
          </div>
        </section>
      </template>
    </div>
  `,
};

export {
  TasksPage,
  TaskDetailPage,
  EntitlementsPage,
  EntitlementDetailPage,
  resultStateLabel,
  cancelStateLabel,
};

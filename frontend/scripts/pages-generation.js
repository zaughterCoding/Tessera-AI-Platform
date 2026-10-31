import { pageCommon } from "./pages-more.js";

// 当前阶段只展示已受理与已冻结事实；没有进度轮询、取消、结算或结果入口。
const TASK_STATE_LABELS = {
  QUEUED: "排队中",
};

const ACCOUNTING_LABELS = {
  FROZEN: "次数已冻结",
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

function accountingLabel(state) {
  return ACCOUNTING_LABELS[state] || state || "未知";
}

function ledgerTypeLabel(type) {
  return LEDGER_TYPE_LABELS[type] || type || "未知";
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
        <span class="muted">共 {{ total }} 个；本页不轮询排队状态</span>
      </div>
      <notice-box v-if="submitStatus && !submitStatus.submitEnabled" tone="warn" title="提交当前未开启">
        {{ submitStatus.notice }}已提交任务的次数冻结与查询不受影响。
      </notice-box>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="!loading && !error" class="card">
        <div v-for="item in records" :key="item.id" class="order-row">
          <div>
            <div class="meta-line">
              <status-badge status="queued" :label="stateLabel(item.state)"></status-badge>
              <status-badge status="frozen" :label="accountingLabel(item.accountingState)"></status-badge>
              <span class="muted">任务号 {{ item.id }}</span>
            </div>
            <h3 style="margin:6px 0 2px">{{ item.applicationName }} · 版本 {{ item.applicationVersion }}</h3>
            <div class="meta-line">
              <span>{{ item.modelVersion }} / {{ item.imageSize }} / {{ item.outputCount }} 张</span>
              <span>冻结 {{ item.frozenUnits }} 次</span>
              <span>{{ $formatTime(item.createdAt) }}</span>
            </div>
            <div class="muted">AI 执行尚未开始；当前不提供结果、取消或结算。</div>
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
    return { id: this.$route.id, task: null };
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
    stateLabel: taskStateLabel,
    accountingLabel,
  },
  template: `
    <div>
      <div class="section-head"><h2>任务详情</h2><a href="#/tasks">返回任务列表</a></div>
      <error-block v-if="error" :error="error" @retry="load"></error-block>
      <loading-block v-if="loading"></loading-block>
      <div v-if="task" class="grid grid-2">
        <div class="card">
          <div class="meta-line">
            <status-badge status="queued" :label="stateLabel(task.state)"></status-badge>
            <status-badge status="frozen" :label="accountingLabel(task.accountingState)"></status-badge>
            <span class="muted">任务号 {{ task.id }}</span>
          </div>
          <h3 style="margin:10px 0 4px">{{ task.applicationName }}</h3>
          <dl class="kv">
            <dt>应用版本</dt><dd>{{ task.applicationVersion }}</dd>
            <dt>模型版本</dt><dd>{{ task.modelVersion }}</dd>
            <dt>输出规格</dt><dd>{{ task.imageSize }} · {{ task.outputCount }} 张</dd>
            <dt>冻结次数</dt><dd>{{ task.frozenUnits }}</dd>
            <dt>提交时间</dt><dd>{{ $formatTime(task.createdAt) }}</dd>
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
            <p class="muted">总次数 = 可用 + 冻结 + 已消费 + 已过期。冻结表示任务已受理但尚未结算；本阶段不执行 AI、不消费冻结次数。</p>
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

export { TasksPage, TaskDetailPage, EntitlementsPage, EntitlementDetailPage };

const BOOT = {
  loading: false,
  error: null,
};

const AdminPage = {
  data() {
    return {
      tab: this.$route.query.tab || "campaigns",
      loading: false,
      error: null,
      forbidden: false,
      campaigns: [],
      campaignForm: { productId: "", initialStock: "", beginAt: "", endAt: "" },
      creatingCampaign: false,
      selectedCampaignId: "",
      selectedCampaign: null,
      reconciliation: null,
      reviews: [],
      deadLetters: [],
      streamMetrics: null,
      acting: "",
      outbox: [],
      outboxState: "",
      failures: [],
      backfill: null,
      backfillMissing: false,
      analyticsFailures: [],
      analyticsOutbox: [],
      audits: [],
      auditFilters: { action: "", objectType: "", outcome: "" },
      auditOffset: 0,
      auditLimit: 50,
      retention: null,
      retentionNotice: null,
      runningTarget: "",
      experience: null,
      experienceError: null,
      cacheTraceInput: "",
      cacheTrace: null,
      progressInput: "",
      progress: null,
      generationState: "",
      generationOffset: 0,
      generationLimit: 20,
      generationOverview: null,
      generationTasks: [],
      generationSelectedId: "",
      generationDelivery: null,
    };
  },
  created() {
    if (!this.$root.user || !this.$root.isAdmin) {
      this.forbidden = true;
      return;
    }
    this.loadTab();
  },
  methods: {
    async loadTab() {
      if (this.forbidden) return;
      this.loading = true;
      this.error = null;
      try {
        if (this.tab === "campaigns") await this.loadCampaigns();
        else if (this.tab === "messaging") await this.loadMessaging();
        else if (this.tab === "analytics") await this.loadAnalytics();
        else if (this.tab === "operations") await this.loadOperations();
        else if (this.tab === "experience") await this.loadExperience();
        else if (this.tab === "generation") await this.loadGeneration();
      } catch (failure) {
        this.error = failure;
      } finally {
        this.loading = false;
      }
    },
    selectTab(tab) {
      this.tab = tab;
      this.error = null;
      const next = "#/admin?tab=" + tab;
      if (window.location.hash === next) this.loadTab();
      else window.location.hash = next;
    },
    notifyError(failure) {
      this.$root.notify((failure && failure.message) || "操作未完成", "error");
    },
    utcInput(value) {
      if (!value) return null;
      const date = new Date(value);
      return Number.isNaN(date.getTime()) ? null : date.toISOString().slice(0, 19);
    },
    async loadGeneration() {
      const [overview, tasks] = await Promise.all([
        this.$api("/admin/generation/overview"),
        this.$api("/admin/generation/tasks", {
          params: {
            state: this.generationState || undefined,
            limit: this.generationLimit,
            offset: this.generationOffset,
          },
        }),
      ]);
      this.generationOverview = overview.data || null;
      this.generationTasks = tasks.data || [];
      if (this.generationSelectedId) await this.selectGeneration(this.generationSelectedId);
    },
    async selectGeneration(id) {
      this.generationSelectedId = id;
      const result = await this.$api("/admin/generation/tasks/" + encodeURIComponent(id)
        + "/delivery");
      this.generationDelivery = result.data || null;
    },
    filterGeneration() {
      this.generationOffset = 0;
      this.loadGeneration().catch((failure) => this.notifyError(failure));
    },
    generationPage(delta) {
      const next = this.generationOffset + delta * this.generationLimit;
      if (next < 0) return;
      this.generationOffset = next;
      this.loadGeneration().catch((failure) => this.notifyError(failure));
    },
    async generationAction(id, action) {
      const paths = {
        retry: "/admin/generation/tasks/" + encodeURIComponent(id) + "/results/retry",
        recover: "/admin/generation/tasks/" + encodeURIComponent(id) + "/settlement/recover",
        cancel: "/admin/generation/tasks/" + encodeURIComponent(id) + "/cancel",
      };
      this.acting = "generation-" + action + "-" + id;
      try {
        const result = await this.$api(paths[action], {
          method: "POST",
          body: action === "cancel" ? { reason: "运营台请求取消" } : undefined,
        });
        const data = result.data || {};
        this.$root.notify(data.applied ? "操作已登记：" + data.outcome
          : "当前事实不满足条件：" + data.outcome, data.applied ? "success" : "warn");
        await this.loadGeneration();
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    async loadCampaigns() {
      const [campaigns, deadLetters, streamMetrics] = await Promise.all([
        this.$api("/admin/campaigns"),
        this.$api("/admin/campaigns/dead-letters", { params: { limit: 20 } }),
        this.$api("/admin/campaigns/stream-metrics"),
      ]);
      this.campaigns = campaigns.data || [];
      this.deadLetters = deadLetters.data || [];
      this.streamMetrics = streamMetrics.data || null;
      if (this.selectedCampaignId) await this.selectCampaign(this.selectedCampaignId);
    },
    async createCampaign() {
      this.creatingCampaign = true;
      try {
        await this.$api("/admin/campaigns", {
          method: "POST",
          body: {
            productId: this.campaignForm.productId,
            initialStock: Number(this.campaignForm.initialStock),
            beginAt: this.utcInput(this.campaignForm.beginAt),
            endAt: this.utcInput(this.campaignForm.endAt),
          },
        });
        this.$root.notify("活动已创建；发布前不会对用户开放。");
        this.campaignForm = { productId: "", initialStock: "", beginAt: "", endAt: "" };
        await this.loadCampaigns();
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.creatingCampaign = false;
      }
    },
    async selectCampaign(id) {
      this.selectedCampaignId = id;
      const [detail, reconciliation, reviews] = await Promise.all([
        this.$api("/admin/campaigns/" + encodeURIComponent(id)),
        this.$api("/admin/campaigns/" + encodeURIComponent(id) + "/reconciliation"),
        this.$api("/admin/campaigns/" + encodeURIComponent(id) + "/review", { params: { limit: 50 } }),
      ]);
      this.selectedCampaign = detail.data;
      this.reconciliation = reconciliation.data;
      this.reviews = reviews.data || [];
    },
    async campaignAction(campaign, action) {
      const text = { publish: "发布", pause: "暂停", recover: "恢复" }[action] || action;
      if (!window.confirm("确认" + text + "活动 " + campaign.id + "？")) return;
      this.acting = campaign.id + action;
      try {
        await this.$api("/admin/campaigns/" + encodeURIComponent(campaign.id) + "/" + action, { method: "POST" });
        this.$root.notify("活动" + text + "已完成；以服务端返回状态为准。");
        await this.loadCampaigns();
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    async retryReservation(orderId) {
      this.acting = "retry-" + orderId;
      try {
        const result = await this.$api("/admin/campaigns/reservations/" + encodeURIComponent(orderId) + "/retry", {
          method: "POST",
        });
        this.$root.notify("预约已重试：" + (result.data.reservationNotice || result.data.state));
        await this.selectCampaign(this.selectedCampaignId);
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    async failReservation(orderId) {
      const reason = window.prompt("请填写人工判定失败的原因（会记录在预约中）", "人工复核确认名额不可用");
      if (!reason) return;
      this.acting = "fail-" + orderId;
      try {
        await this.$api("/admin/campaigns/reservations/" + encodeURIComponent(orderId) + "/fail", {
          method: "POST",
          body: { reason },
        });
        this.$root.notify("预约已标记失败，名额按服务端规则释放。");
        await this.selectCampaign(this.selectedCampaignId);
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    async replayDeadLetter(messageId) {
      this.acting = "replay-" + messageId;
      try {
        await this.$api("/admin/campaigns/dead-letters/" + encodeURIComponent(messageId) + "/replay", { method: "POST" });
        this.$root.notify("死信重放已受理。");
        await this.loadCampaigns();
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    async loadMessaging() {
      const params = { limit: 50, offset: 0 };
      if (this.outboxState) params.state = this.outboxState;
      const [outbox, failures] = await Promise.all([
        this.$api("/admin/messaging/outbox", { params }),
        this.$api("/admin/messaging/failures", { params: { limit: 50, offset: 0 } }),
      ]);
      this.outbox = outbox.data || [];
      this.failures = failures.data || [];
    },
    async retryTask(task) {
      this.acting = "task-" + task.id;
      try {
        await this.$api("/admin/messaging/outbox/" + encodeURIComponent(task.id) + "/retry", { method: "POST" });
        this.$root.notify("任务已重新进入投递队列。");
        await this.loadMessaging();
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    async loadAnalytics() {
      const [backfill, failures, outbox] = await Promise.all([
        this.$api("/admin/analytics/backfill").catch((failure) => {
          if (failure.status === 404) {
            this.backfillMissing = true;
            return null;
          }
          throw failure;
        }),
        this.$api("/admin/analytics/failures", { params: { limit: 50, offset: 0 } }),
        this.$api("/admin/analytics/outbox", { params: { limit: 50, offset: 0 } }),
      ]);
      this.backfill = backfill ? backfill.data : null;
      this.analyticsFailures = failures.data || [];
      this.analyticsOutbox = outbox.data || [];
    },
    async restartBackfill() {
      if (!window.confirm("重新开始补数会从当前记录的起点推进；确认继续？")) return;
      this.acting = "backfill";
      try {
        await this.$api("/admin/analytics/backfill", { method: "POST" });
        this.$root.notify("补数任务已重新启动。");
        await this.loadAnalytics();
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    async retryAnalyticsOutbox(event) {
      this.acting = "analytics-" + event.eventId;
      try {
        await this.$api("/admin/analytics/outbox/" + encodeURIComponent(event.eventId) + "/retry", { method: "POST" });
        this.$root.notify("分析事件已重新进入投递队列。");
        await this.loadAnalytics();
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    async loadOperations() {
      const params = { limit: this.auditLimit, offset: this.auditOffset };
      if (this.auditFilters.action) params.action = this.auditFilters.action;
      if (this.auditFilters.objectType) params.objectType = this.auditFilters.objectType;
      if (this.auditFilters.outcome) params.outcome = this.auditFilters.outcome;
      const [audits, retention] = await Promise.all([
        this.$api("/admin/operations/audit", { params }),
        this.$api("/admin/operations/retention"),
      ]);
      this.audits = audits.data || [];
      this.retention = retention.data;
    },
    searchAudit() {
      this.auditOffset = 0;
      this.loadOperations().catch((failure) => this.notifyError(failure));
    },
    auditPage(delta) {
      const next = this.auditOffset + delta * this.auditLimit;
      if (next < 0) return;
      this.auditOffset = next;
      this.loadOperations().catch((failure) => this.notifyError(failure));
    },
    async runRetention(row) {
      if (!window.confirm("将按保留期清理 " + row.label + "，最多 10 批；在途、复核与仍被引用的数据由服务端条件排除。确认执行？")) return;
      this.runningTarget = row.target;
      this.retentionNotice = null;
      try {
        const result = await this.$api("/admin/operations/retention/" + encodeURIComponent(row.target) + "/run", {
          method: "POST",
          body: { maxBatches: 10 },
        });
        this.retentionNotice = result.data;
        this.$root.notify("清理完成，删除 " + result.data.deleted + " 行，剩余 " + result.data.remaining + " 行。");
        await this.loadOperations();
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.runningTarget = "";
      }
    },
    async loadExperience() {
      this.experienceError = null;
      try {
        const result = await this.$api("/admin/experience/capabilities");
        this.experience = result.data;
      } catch (failure) {
        if (failure.status === 404) {
          this.experience = null;
          this.experienceError = failure;
          return;
        }
        throw failure;
      }
    },
    async traceCache() {
      if (!this.cacheTraceInput) return;
      this.acting = "trace";
      this.cacheTrace = null;
      try {
        const result = await this.$api("/admin/experience/cache-trace/" + encodeURIComponent(this.cacheTraceInput));
        this.cacheTrace = result.data;
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    async loadProgress() {
      if (!this.progressInput) return;
      this.acting = "progress";
      this.progress = null;
      try {
        const result = await this.$api("/admin/experience/orders/" + encodeURIComponent(this.progressInput) + "/progress");
        this.progress = result.data;
      } catch (failure) {
        this.notifyError(failure);
      } finally {
        this.acting = "";
      }
    },
    gaugeText(value) {
      return value == null ? "未知（尚未采集）" : String(value);
    },
    eventText(event) {
      return event.layer + " · " + event.result;
    },
  },
  template: `
    <div class="admin-panel">
      <div class="section-head"><h2>运营控制台</h2><span class="muted">所有操作需服务端 ADMIN 角色；按钮隐藏不代表授权。</span></div>
      <div v-if="forbidden" class="notice notice-danger">当前账号没有 ADMIN 角色，请使用管理员账号登录。</div>
      <template v-else>
        <admin-tabs :current="tab" @select="selectTab"></admin-tabs>
        <error-block v-if="error" :error="error" @retry="loadTab"></error-block>
        <loading-block v-if="loading"></loading-block>

        <div v-if="!loading && tab === 'campaigns'" class="admin-section">
          <div class="card">
            <h3>创建活动</h3>
            <div class="form-grid">
              <label class="field"><span>商品 ID</span><input v-model.trim="campaignForm.productId" placeholder="限量商品 ID" /></label>
              <label class="field"><span>初始库存</span><input v-model.trim="campaignForm.initialStock" inputmode="numeric" placeholder="正整数" /></label>
              <label class="field"><span>开始时间（本地时区输入，按 UTC 提交）</span><input v-model="campaignForm.beginAt" type="datetime-local" /></label>
              <label class="field"><span>结束时间</span><input v-model="campaignForm.endAt" type="datetime-local" /></label>
            </div>
            <div class="actions" style="margin-top:12px">
              <button class="button" type="button" :disabled="creatingCampaign" @click="createCampaign">{{ creatingCampaign ? '创建中…' : '创建活动' }}</button>
              <span class="muted">创建后需发布；发布状态与库存由服务端校验。</span>
            </div>
          </div>

          <div class="card" style="margin-top:16px">
            <h3>活动列表</h3>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>ID</th><th>应用 / 商品</th><th>库存</th><th>状态</th><th>时间</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="item in campaigns" :key="item.id">
                    <td>{{ item.id }}</td>
                    <td>{{ item.applicationName }}<br /><span class="muted">{{ item.productTitle }}</span></td>
                    <td>{{ item.remainingStock }} / {{ item.initialStock }}</td>
                    <td><status-badge :status="item.publishState" :label="item.publishState"></status-badge></td>
                    <td class="muted">{{ $formatTime(item.beginAt) }}<br />至 {{ $formatTime(item.endAt) }}</td>
                    <td>
                      <div class="actions">
                        <button v-if="item.publishState === 'DRAFT'" class="button small" type="button" :disabled="acting === item.id + 'publish'" @click="campaignAction(item, 'publish')">发布</button>
                        <button v-if="item.publishState === 'ACTIVE'" class="button ghost small" type="button" :disabled="acting === item.id + 'pause'" @click="campaignAction(item, 'pause')">暂停</button>
                        <button v-if="item.publishState === 'PAUSED'" class="button ghost small" type="button" :disabled="acting === item.id + 'recover'" @click="campaignAction(item, 'recover')">恢复</button>
                        <button class="button ghost small" type="button" @click="selectCampaign(item.id)">核对</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!campaigns.length" text="还没有活动"></empty-block>
          </div>

          <div v-if="selectedCampaign" class="card" style="margin-top:16px">
            <h3>活动 {{ selectedCampaign.id }} 核对</h3>
            <div v-if="reconciliation" class="grid grid-2">
              <dl class="kv">
                <dt>账面库存</dt><dd>{{ reconciliation.remainingStock }} / {{ reconciliation.initialStock }}</dd>
                <dt>已提交订单</dt><dd>{{ reconciliation.committedOrders }}</dd>
                <dt>在途预约</dt><dd>{{ reconciliation.inFlightReservations }}</dd>
                <dt>复核预约</dt><dd>{{ reconciliation.reviewReservations }}</dd>
                <dt>公式一致</dt><dd>{{ reconciliation.formulaMatched ? '是' : '否' }}</dd>
                <dt>完全一致</dt><dd>{{ reconciliation.exact ? '是' : '否' }}</dd>
              </dl>
              <dl class="kv">
                <dt>Redis 库存</dt><dd>{{ reconciliation.redisStock }}</dd>
                <dt>Redis Generation</dt><dd>{{ reconciliation.redisGeneration }}</dd>
                <dt>库存差异</dt><dd>{{ reconciliation.redisDifference }}（{{ reconciliation.stockDifferenceKind || '未知' }}）</dd>
                <dt>待处理消息</dt><dd>{{ reconciliation.pendingMessages }}</dd>
                <dt>Stream 延迟</dt><dd>{{ reconciliation.streamLag }}</dd>
                <dt>未知延迟消费组</dt><dd>{{ reconciliation.unknownLagGroups }}</dd>
              </dl>
            </div>
            <p class="muted">{{ reconciliation && reconciliation.note }}</p>
            <h4>待复核预约</h4>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>订单</th><th>状态</th><th>尝试</th><th>最后错误</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="row in reviews" :key="row.orderId">
                    <td>{{ row.orderId }}</td>
                    <td>{{ row.state }}</td>
                    <td>{{ row.attempts }}</td>
                    <td class="muted">{{ row.lastError || '—' }}</td>
                    <td>
                      <div class="actions">
                        <button class="button small" type="button" :disabled="acting === 'retry-' + row.orderId" @click="retryReservation(row.orderId)">重试</button>
                        <button class="button danger small" type="button" :disabled="acting === 'fail-' + row.orderId" @click="failReservation(row.orderId)">判定失败</button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!reviews.length" text="没有待复核预约"></empty-block>
            <h4>Stream 安全边界</h4>
            <dl v-if="streamMetrics" class="kv">
              <dt>Stream 长度</dt><dd>{{ streamMetrics.streamLength }}</dd>
              <dt>Pending</dt><dd>{{ streamMetrics.pendingCount }}</dd>
              <dt>延迟</dt><dd>{{ streamMetrics.streamLag }}</dd>
              <dt>最老 Pending</dt><dd>{{ streamMetrics.oldestPendingAgeMillis == null ? '未知' : streamMetrics.oldestPendingAgeMillis + ' ms' }}</dd>
              <dt>可安全裁剪</dt><dd>{{ streamMetrics.safeToTrim ? '是' : '否' }}</dd>
              <dt>阻塞原因</dt><dd>{{ streamMetrics.blockedReason || '—' }}</dd>
              <dt>消费组</dt><dd>{{ streamMetrics.consumerGroups }}</dd>
            </dl>
          </div>

          <div class="card" style="margin-top:16px">
            <h3>死信</h3>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>消息</th><th>活动 / 订单</th><th>原因</th><th>时间</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="row in deadLetters" :key="row.messageId">
                    <td>{{ row.messageId }}</td>
                    <td>{{ row.campaignId }} / {{ row.orderId }}</td>
                    <td class="muted">{{ row.reason }}</td>
                    <td class="muted">{{ $formatTime(row.createdAt) }}</td>
                    <td><button class="button small" type="button" :disabled="acting === 'replay-' + row.messageId" @click="replayDeadLetter(row.messageId)">重放</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!deadLetters.length" text="没有死信"></empty-block>
          </div>
        </div>

        <div v-if="!loading && tab === 'messaging'" class="admin-section">
          <div class="card">
            <h3>可靠任务</h3>
            <div class="form-row" style="margin-bottom:12px">
              <label class="field"><span>状态</span>
                <select v-model="outboxState" @change="loadMessaging()">
                  <option value="">全部</option><option>PENDING</option><option>SENDING</option><option>SENT</option><option>DONE</option><option>REVIEW</option>
                </select>
              </label>
              <button class="button ghost" type="button" @click="loadMessaging()">刷新</button>
            </div>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>ID</th><th>类型</th><th>聚合</th><th>状态</th><th>尝试</th><th>最后错误</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="task in outbox" :key="task.id">
                    <td>{{ task.id }}</td><td>{{ task.kind }}</td><td>{{ task.aggregateId }}</td>
                    <td>{{ task.state }}</td><td>{{ task.attempts }} / {{ task.consumeAttempts }}</td>
                    <td class="muted">{{ task.lastError || '—' }}</td>
                    <td><button v-if="task.state === 'REVIEW'" class="button small" type="button" :disabled="acting === 'task-' + task.id" @click="retryTask(task)">重试</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!outbox.length" text="没有符合条件的任务"></empty-block>
          </div>
          <div class="card" style="margin-top:16px">
            <h3>失败隔离</h3>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>消费组</th><th>事件键</th><th>事件 ID</th><th>原因</th><th>时间</th></tr></thead>
                <tbody>
                  <tr v-for="row in failures" :key="row.consumerGroup + row.eventKey">
                    <td>{{ row.consumerGroup }}</td><td>{{ row.eventKey }}</td><td>{{ row.eventId }}</td>
                    <td class="muted">{{ row.reason }}</td><td class="muted">{{ $formatTime(row.createdAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!failures.length" text="没有失败隔离记录"></empty-block>
          </div>
        </div>

        <div v-if="!loading && tab === 'generation'" class="admin-section">
          <div v-if="generationOverview" class="grid grid-3">
            <div class="card">
              <h3 style="margin-top:0">任务状态</h3>
              <div v-for="(count, state) in generationOverview.taskStates" :key="'t' + state" class="meta-line">
                <span>{{ state }}</span><strong>{{ count }}</strong>
              </div>
            </div>
            <div class="card">
              <h3 style="margin-top:0">结果状态</h3>
              <div v-for="(count, state) in generationOverview.resultStates" :key="'r' + state" class="meta-line">
                <span>{{ state }}</span><strong>{{ count }}</strong>
              </div>
            </div>
            <div class="card">
              <h3 style="margin-top:0">取消请求</h3>
              <div v-for="(count, state) in generationOverview.cancelStates" :key="'c' + state" class="meta-line">
                <span>{{ state }}</span><strong>{{ count }}</strong>
              </div>
            </div>
          </div>
          <div class="card" style="margin-top:16px">
            <h3>任务交付</h3>
            <div class="form-row" style="margin-bottom:12px">
              <label class="field"><span>任务状态</span>
                <select v-model="generationState" @change="filterGeneration">
                  <option value="">全部</option>
                  <option>QUEUED</option><option>DISPATCHING</option><option>SUBMITTED</option>
                  <option>RUNNING</option><option>RECONCILING</option><option>RESULT_PENDING</option>
                  <option>FAILED_PENDING_FOLLOWUP</option><option>CANCELLED_PENDING_FOLLOWUP</option>
                  <option>QUEUE_TIMEOUT</option><option>REVIEW</option><option>CANCEL_REQUESTED</option>
                  <option>SUCCEEDED</option><option>FAILED</option><option>CANCELLED</option>
                </select>
              </label>
              <button class="button ghost" type="button" @click="filterGeneration">刷新</button>
            </div>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>ID</th><th>用户</th><th>状态</th><th>账务</th><th>规格</th><th>时间</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="row in generationTasks" :key="row.id">
                    <td>{{ row.id }}</td><td>{{ row.userId }}</td><td>{{ row.state }}</td>
                    <td>{{ row.accountingState }}</td>
                    <td>{{ row.attemptState || '—' }} / {{ row.frozenUnits }} 次</td>
                    <td class="muted">{{ $formatTime(row.createdAt) }}</td>
                    <td><button class="button ghost small" type="button" @click="selectGeneration(row.id)">核对</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!generationTasks.length" text="没有符合条件的任务"></empty-block>
            <div class="actions" style="margin-top:12px">
              <button class="button ghost small" type="button" :disabled="generationOffset <= 0" @click="generationPage(-1)">上一页</button>
              <span class="muted">偏移 {{ generationOffset }}</span>
              <button class="button ghost small" type="button" :disabled="generationTasks.length < generationLimit" @click="generationPage(1)">下一页</button>
            </div>
          </div>
          <div v-if="generationDelivery" class="card" style="margin-top:16px">
            <h3>任务 {{ generationDelivery.taskId }} · {{ generationDelivery.taskState }} / {{ generationDelivery.accountingState }}</h3>
            <p class="muted">已保存 {{ generationDelivery.savedCount }} / {{ generationDelivery.outputCount }}；重试只重置保存失败的结果，不会改写任务决定或余额。</p>
            <div class="actions" style="margin-bottom:12px">
              <button class="button small" type="button"
                :disabled="acting === 'generation-retry-' + generationDelivery.taskId"
                @click="generationAction(generationDelivery.taskId, 'retry')">重试结果保存</button>
              <button class="button ghost small" type="button"
                :disabled="acting === 'generation-recover-' + generationDelivery.taskId"
                @click="generationAction(generationDelivery.taskId, 'recover')">按事实补结算</button>
              <button class="button danger small" type="button"
                :disabled="acting === 'generation-cancel-' + generationDelivery.taskId"
                @click="generationAction(generationDelivery.taskId, 'cancel')">请求取消</button>
            </div>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>序号</th><th>状态</th><th>尝试</th><th>媒体</th><th>摘要</th><th>最后错误</th><th>下次尝试</th></tr></thead>
                <tbody>
                  <tr v-for="result in generationDelivery.results" :key="result.id">
                    <td>{{ result.position }}</td><td>{{ result.state }}</td>
                    <td>{{ result.saveAttempts }}</td><td>{{ result.mediaId || '—' }}</td>
                    <td class="muted">{{ result.contentDigest || result.expectedDigest || '—' }}</td>
                    <td class="muted">{{ result.lastError || '—' }}</td>
                    <td class="muted">{{ $formatTime(result.nextAttemptAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!generationDelivery.results.length" text="尚未登记结果文件；旧版元数据任务保持冻结等待人工处理"></empty-block>
            <p v-if="generationDelivery.cancelRequest" class="muted">
              取消请求：{{ generationDelivery.cancelRequest.state }} /
              {{ generationDelivery.cancelRequest.outcome || '等待确认' }}
            </p>
            <p v-if="generationDelivery.settlement" class="muted">
              结算：{{ generationDelivery.settlement.description }}
            </p>
          </div>
        </div>

        <div v-if="!loading && tab === 'analytics'" class="admin-section">
          <div class="card">
            <h3>补数状态</h3>
            <div v-if="backfillMissing" class="notice notice-warn">尚未登记补数任务；分析开启并产生事件后才会创建。</div>
            <dl v-else-if="backfill" class="kv">
              <dt>状态</dt><dd>{{ backfill.state }}</dd>
              <dt>游标事件</dt><dd>{{ backfill.cursorEventId }}</dd>
              <dt>范围起点</dt><dd>{{ $formatTime(backfill.rangeFrom) }}</dd>
              <dt>已处理</dt><dd>{{ backfill.processed }}</dd>
              <dt>更新时间</dt><dd>{{ $formatTime(backfill.updatedAt) }}</dd>
              <dt>最后错误</dt><dd>{{ backfill.lastError || '—' }}</dd>
            </dl>
            <button class="button" type="button" style="margin-top:12px" :disabled="acting === 'backfill'" @click="restartBackfill">重新开始补数</button>
          </div>
          <div class="card" style="margin-top:16px">
            <h3>分析失败隔离</h3>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Topic</th><th>分区 / Offset</th><th>原因</th><th>时间</th></tr></thead>
                <tbody>
                  <tr v-for="row in analyticsFailures" :key="row.topic + row.partition + row.offset">
                    <td>{{ row.topic }}</td><td>{{ row.partition }} / {{ row.offset }}</td>
                    <td class="muted">{{ row.reason }}</td><td class="muted">{{ $formatTime(row.createdAt) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!analyticsFailures.length" text="没有分析失败记录"></empty-block>
          </div>
          <div class="card" style="margin-top:16px">
            <h3>分析 Outbox</h3>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>事件</th><th>应用</th><th>动作</th><th>状态</th><th>尝试</th><th>操作</th></tr></thead>
                <tbody>
                  <tr v-for="event in analyticsOutbox" :key="event.eventId">
                    <td>{{ event.eventId }}</td><td>{{ event.applicationId }}</td><td>{{ event.action }}</td>
                    <td>{{ event.state }}</td><td>{{ event.attempts }}</td>
                    <td><button v-if="event.state === 'REVIEW'" class="button small" type="button" :disabled="acting === 'analytics-' + event.eventId" @click="retryAnalyticsOutbox(event)">重试</button></td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!analyticsOutbox.length" text="没有分析 Outbox 事件"></empty-block>
          </div>
        </div>

        <div v-if="!loading && tab === 'operations'" class="admin-section">
          <div class="card">
            <h3>操作审计</h3>
            <div class="form-row" style="margin-bottom:12px">
              <label class="field"><span>动作</span><input v-model.trim="auditFilters.action" placeholder="如 campaign.publish" /></label>
              <label class="field"><span>对象类型</span><input v-model.trim="auditFilters.objectType" placeholder="如 campaign" /></label>
              <label class="field"><span>结果</span>
                <select v-model="auditFilters.outcome"><option value="">全部</option><option>SUCCESS</option><option>FAILURE</option></select>
              </label>
              <button class="button" type="button" @click="searchAudit">查询</button>
            </div>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>时间</th><th>操作者</th><th>动作 / 对象</th><th>结果</th><th>请求编号</th><th>详情</th></tr></thead>
                <tbody>
                  <tr v-for="row in audits" :key="row.id">
                    <td class="muted">{{ $formatTime(row.occurredAt) }}</td>
                    <td>{{ row.operatorId }}<br /><span class="muted">{{ row.operatorRoles }}</span></td>
                    <td>{{ row.action }}<br /><span class="muted">{{ row.objectType }} {{ row.objectId || '' }}</span></td>
                    <td><status-badge :status="row.outcome" :label="row.outcome === 'SUCCESS' ? '成功' : '失败'"></status-badge>
                      <div v-if="row.errorCode" class="muted">{{ row.errorCode }}</div></td>
                    <td class="muted">{{ row.requestId }}</td>
                    <td class="muted">{{ row.detail || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <empty-block v-if="!audits.length" text="没有审计记录"></empty-block>
            <div class="actions" style="margin-top:10px">
              <button class="button ghost small" type="button" :disabled="auditOffset <= 0" @click="auditPage(-1)">上一页</button>
              <span class="muted">偏移 {{ auditOffset }}</span>
              <button class="button ghost small" type="button" :disabled="audits.length < auditLimit" @click="auditPage(1)">下一页</button>
              <span class="muted">审计以服务端记录的业务结果为准，不只看 HTTP 200。</span>
            </div>
          </div>
          <div class="card" style="margin-top:16px">
            <h3>数据保留</h3>
            <div v-if="retention">
              <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>目标</th><th>规则</th><th>保留天数</th><th>当前可清理</th><th>操作</th></tr></thead>
                  <tbody>
                    <tr v-for="row in retention.database" :key="row.target">
                      <td>{{ row.label }}<br /><span class="muted">{{ row.target }}</span></td>
                      <td class="muted">{{ row.rule }}</td>
                      <td>{{ row.retentionDays }}</td>
                      <td>{{ row.eligible }}</td>
                      <td><button class="button small" type="button" :disabled="runningTarget === row.target" @click="runRetention(row)">{{ runningTarget === row.target ? '执行中…' : '执行清理' }}</button></td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div class="grid grid-2" style="margin-top:14px">
                <div class="notice notice-info">
                  <strong>媒体文件</strong>
                  <span>{{ retention.media.rule }}</span>
                  <span class="muted">PENDING {{ retention.media.pending }} · ACTIVE {{ retention.media.active }} · DELETING {{ retention.media.deleting }} · DELETED {{ retention.media.deleted }}</span>
                </div>
                <div class="notice notice-info">
                  <strong>分析数据</strong>
                  <span>明细保留 {{ retention.analyticsRetentionDays }} 天，补数窗口 {{ retention.analyticsBackfillDays }} 天。</span>
                  <span class="muted">{{ retention.analyticsNote }}</span>
                </div>
              </div>
              <notice-box v-if="retentionNotice" tone="success" title="本次清理结果">
                目标 {{ retentionNotice.target }}；截止 {{ $formatTime(retentionNotice.cutoff) }}；批次 {{ retentionNotice.batches }}；删除 {{ retentionNotice.deleted }}；剩余 {{ retentionNotice.remaining }}。
              </notice-box>
            </div>
          </div>
        </div>

        <div v-if="!loading && tab === 'experience'" class="admin-section">
          <div v-if="experienceError" class="notice notice-warn">
            技术体验未启用或当前账号无权访问；默认关闭，且只允许在显式 local/test 配置下开启。
          </div>
          <template v-else-if="experience">
            <div class="card">
              <h3>运行能力</h3>
              <dl class="kv">
                <dt>缓存</dt><dd>{{ experience.cacheEnabled ? '开启' : '关闭' }}；Memcache {{ experience.memcacheEnabled ? '开启' : '关闭' }}</dd>
                <dt>消息传输</dt><dd>{{ experience.messagingTransport }}</dd>
                <dt>行为分析</dt><dd>{{ experience.analyticsEnabled ? '开启' : '关闭' }}</dd>
                <dt>CDC</dt><dd>{{ experience.cdcEnabled ? '开启' : '关闭' }}；连接 {{ gaugeText(experience.cdcConnected) }}</dd>
                <dt>分析消费者</dt><dd>运行 {{ gaugeText(experience.analyticsConsumerRunning) }}；延迟 {{ gaugeText(experience.analyticsConsumerLag) }}</dd>
                <dt>Outbox PENDING</dt><dd>{{ gaugeText(experience.outboxPending) }}</dd>
                <dt>采样时间</dt><dd>{{ $formatTime(experience.sampledAt) }}</dd>
              </dl>
              <p class="muted">以上为读取真实指标与配置的即时值，采样缺失显示“未知”，不代表零。</p>
            </div>
            <div class="card" style="margin-top:16px">
              <h3>缓存读取轨迹</h3>
              <div class="form-row">
                <label class="field"><span>应用 ID</span><input v-model.trim="cacheTraceInput" placeholder="输入已发布应用 ID" /></label>
                <button class="button" type="button" :disabled="acting === 'trace'" @click="traceCache">读取两次</button>
              </div>
              <div v-if="cacheTrace">
                <p>应用：{{ cacheTrace.applicationName || '未读取到' }}</p>
                <div v-for="sample in cacheTrace.reads" :key="sample.sequence" class="timeline-item">
                  第 {{ sample.sequence }} 次，耗时 {{ sample.durationMicros }} μs：
                  <span v-for="(event, index) in sample.events" :key="index" class="chip">{{ eventText(event) }}</span>
                </div>
              </div>
            </div>
            <div class="card" style="margin-top:16px">
              <h3>订单可靠任务进度</h3>
              <div class="form-row">
                <label class="field"><span>订单 ID</span><input v-model.trim="progressInput" placeholder="本人订单 ID" /></label>
                <button class="button" type="button" :disabled="acting === 'progress'" @click="loadProgress">查看</button>
              </div>
              <div v-if="progress">
                <dl class="kv">
                  <dt>订单状态</dt><dd>{{ progress.order.status }}</dd>
                  <dt>预约状态</dt><dd>{{ progress.reservationState || '非活动订单' }}</dd>
                  <dt>名额已释放</dt><dd>{{ progress.stockReleased == null ? '—' : progress.stockReleased ? '是' : '否' }}</dd>
                  <dt>预约尝试</dt><dd>{{ progress.reservationAttempts == null ? '—' : progress.reservationAttempts }}</dd>
                </dl>
                <div class="table-wrap">
                  <table class="data">
                    <thead><tr><th>任务</th><th>类型</th><th>状态</th><th>发送尝试</th><th>消费尝试</th><th>创建</th><th>消费完成</th></tr></thead>
                    <tbody>
                      <tr v-for="task in progress.tasks" :key="task.id">
                        <td>{{ task.id }}</td><td>{{ task.kind }}</td><td>{{ task.state }}</td>
                        <td>{{ task.attempts }}</td><td>{{ task.consumeAttempts }}</td>
                        <td class="muted">{{ $formatTime(task.createdAt) }}</td>
                        <td class="muted">{{ task.consumedAt ? $formatTime(task.consumedAt) : '—' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
                <empty-block v-if="!progress.tasks || !progress.tasks.length" text="该订单没有可靠任务"></empty-block>
              </div>
            </div>
          </template>
        </div>
      </template>
    </div>
  `,
};

export { AdminPage };

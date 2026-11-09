/* 界面语言：<html lang="en"> 的页面使用英文文案，其余用中文 */
const LANG = (document.documentElement.lang || 'zh').toLowerCase().startsWith('en') ? 'en' : 'zh';
const I18N = {
  zh: { prev: '上一步 (←)', play: '播放/暂停 (空格)', next: '下一步 (→)', chapter: '章节', speed: '速度',
    lang: 'English', home: '目录', uncommitted: '未提交·', waiting: '等锁', exec: '正在执行', clock: '数据库时钟',
    st: { run: '进行中', wait: '等待行锁…', ok: '已提交', rb: '已回滚', ret: '直接返回' },
    product: '标准积分包', spec: '100 积分 · 有效 30 天', buy: '立即购买', empty: '（空）' },
  en: { prev: 'Previous (←)', play: 'Play / Pause (Space)', next: 'Next (→)', chapter: 'Chapter', speed: 'Speed',
    lang: '中文', home: 'Index', uncommitted: 'uncommitted·', waiting: 'waiting', exec: 'Executing', clock: 'DB clock',
    st: { run: 'running', wait: 'waiting for lock…', ok: 'committed', rb: 'rolled back', ret: 'returned' },
    product: 'Standard credit pack', spec: '100 credits · valid 30 days', buy: 'Buy now', empty: '(empty)' },
}[LANG];

/* 播放器 DOM：引擎自己生成舞台和控制条 */
document.body.insertAdjacentHTML('afterbegin', `<div id="wrap"><div id="stage"></div></div>
<div id="ctl">
  <button id="bHome" title="${I18N.home}">☰</button>
  <button id="bPrev" title="${I18N.prev}">⏮</button>
  <button id="bPlay" title="${I18N.play}">▶</button>
  <button id="bNext" title="${I18N.next}">⏭</button>
  <select id="sCh" title="${I18N.chapter}"></select>
  <div id="bar"></div>
  <span id="pos"></span>
  <select id="sSp" title="${I18N.speed}"><option value="0.75">0.75×</option><option value="1" selected>1×</option><option value="1.25">1.25×</option><option value="1.5">1.5×</option><option value="2">2×</option></select>
  <button id="bLang">${I18N.lang}</button>
</div>`);
/* ============ 引擎 ============ */
const S = { instant: false, speed: 1, timers: [] };
const stage = document.getElementById('stage');
const $ = id => document.getElementById(id);
const esc = s => String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');

function later(ms, fn) {
  if (S.instant) { fn(); return; }
  S.timers.push(setTimeout(fn, ms / S.speed));
}
function enter(e) {
  if (S.instant) return;
  e.classList.add('enter');
  void e.offsetWidth; setTimeout(() => e.classList.remove('enter'), 30);
}
function pos(e, x, y, w, h) {
  if (x != null) e.style.left = x + 'px';
  if (y != null) e.style.top = y + 'px';
  if (w != null) e.style.width = w + 'px';
  if (h != null) e.style.height = h + 'px';
}
// 创建或更新一个绝对定位元素
function put(id, cls, html, x, y, w, h) {
  let e = $(id); const fresh = !e;
  if (!e) { e = document.createElement('div'); e.id = id; stage.appendChild(e); }
  e.className = 'abs ' + cls;
  if (html != null) e.innerHTML = html;
  pos(e, x, y, w, h);
  if (fresh) enter(e);
  return e;
}
function gone(id) {
  const e = typeof id === 'string' ? $(id) : id; if (!e) return;
  if (S.instant) { e.remove(); return; }
  e.classList.add('leave'); later(480, () => e.remove());
}
function flash(e) { if (S.instant || !e) return; e.classList.remove('flash'); void e.offsetWidth; e.classList.add('flash'); }

// 飞行的信封：只在动画模式出现，回放时跳过
function fly(text, from, to, cls, opt = {}) {
  if (S.instant) return;
  const e = document.createElement('div');
  e.className = 'abs env ' + (cls || '');
  e.innerHTML = text; pos(e, from[0], from[1]); stage.appendChild(e);
  void e.offsetWidth; setTimeout(() => pos(e, to[0], to[1]), 30);
  if (opt.lost) setTimeout(() => e.classList.add('lost'), 650 / S.speed);
  S.timers.push(setTimeout(() => e.remove(), (opt.keep || 1300) / S.speed));
}
function ping(x, y) {
  if (S.instant) return;
  const e = document.createElement('div'); e.className = 'abs ping'; pos(e, x - 6, y - 6, 12, 12);
  e.style.transition = 'all .9s ease-out'; stage.appendChild(e);
  void e.offsetWidth; setTimeout(() => { pos(e, x - 34, y - 34, 68, 68); e.style.opacity = 0; }, 30);
  S.timers.push(setTimeout(() => e.remove(), 1000 / S.speed));
}

/* ---- 表格 ---- */
const T = {};
function table(id, x, y, w, name, sub, cols) {
  const e = put('tb_' + id, 'tbl', '', x, y, w);
  e.innerHTML = `<div class="tn">${name}<small>${sub || ''}</small></div>`;
  const h = document.createElement('div'); h.className = 'tr th';
  cols.forEach(([k, l, cw]) => { const c = document.createElement('div'); c.className = 'td'; c.textContent = l; c.style.width = cw + 'px'; h.appendChild(c); });
  e.appendChild(h); T[id] = { cols, el: e, y };
}
const R = (tid, rid) => $(`r_${tid}_${rid}`);
function row(tid, rid, vals, tx) {
  const t = T[tid]; const r = document.createElement('div'); r.className = 'tr'; r.id = `r_${tid}_${rid}`;
  t.cols.forEach(([k, , cw]) => { const c = document.createElement('div'); c.className = 'td'; c.dataset.k = k; c.style.width = cw + 'px'; c.innerHTML = vals[k] ?? ''; r.appendChild(c); });
  const b = document.createElement('div'); b.className = 'badges'; r.appendChild(b);
  if (tx) { r.classList.add('pending'); r.dataset.tx = tx; badge(r, 'tx_' + tx, 'tx', I18N.uncommitted + tx); }
  t.el.appendChild(r);
  if (!S.instant) { r.style.opacity = 0; r.style.transform = 'translateX(18px)'; void r.offsetWidth; setTimeout(() => { r.style.opacity = ''; r.style.transform = ''; }, 30); }
  return r;
}
function cell(tid, rid, k, v, tx) {
  const c = R(tid, rid).querySelector(`[data-k="${k}"]`);
  if (tx && !c.dataset.tx) { c.dataset.old = c.innerHTML; c.dataset.tx = tx; c.classList.add('pend'); }
  c.innerHTML = v; flash(c);
}
function badge(r, key, cls, text) {
  const b = r.querySelector('.badges'); let x = b.querySelector(`[data-key="${key}"]`);
  if (!x) { x = document.createElement('span'); x.dataset.key = key; b.appendChild(x); }
  x.className = 'bd ' + cls; x.innerHTML = text;
}
function unbadge(r, key) { const x = r && r.querySelector(`.badges [data-key="${key}"]`); if (x) x.remove(); }
function lock(tid, rid, type, tx) { badge(R(tid, rid), 'lk_' + tx, type, `🔒${type} ${tx}`); }
function waitLock(tid, rid, tx) { badge(R(tid, rid), 'lk_' + tx, 'wait', `⏳${tx} ${I18N.waiting}`); }
function unlock(tx) { stage.querySelectorAll(`.badges [data-key="lk_${tx}"]`).forEach(x => x.remove()); }
function commit(tx) {
  stage.querySelectorAll(`.tr[data-tx="${tx}"]`).forEach(r => { r.classList.remove('pending'); delete r.dataset.tx; unbadge(r, 'tx_' + tx); });
  stage.querySelectorAll(`.td[data-tx="${tx}"]`).forEach(c => { c.classList.remove('pend'); delete c.dataset.tx; delete c.dataset.old; });
  unlock(tx);
}
function rollback(tx) {
  stage.querySelectorAll(`.tr[data-tx="${tx}"]`).forEach(r => { r.classList.add('rb'); later(700, () => gone(r)); });
  stage.querySelectorAll(`.td[data-tx="${tx}"]`).forEach(c => { c.innerHTML = c.dataset.old; c.classList.remove('pend'); delete c.dataset.tx; });
  unlock(tx);
}
function rmRow(tid, rid) { gone(R(tid, rid)); }
function hl(tid, rid, cls = 'hl') { const r = R(tid, rid); if (r) { r.classList.remove('hl', 'hlg', 'hlr'); if (cls) r.classList.add(cls); } }
function dim(tid, rid, on = true) { const r = R(tid, rid); if (r) r.classList.toggle('dim', on); }

/* ---- 应用栏组件 ---- */
const ST = I18N.st;
// 中栏位置：默认与三栏布局一致，单集可通过 EP.lanes 的宽度覆盖
const M = { x: 306, w: 384 };
function tx(id, slot, label, st = 'run', stText) { card(id, M.x, 88 + slot * 46, M.w, label, st, stText); }
// 任意位置的状态卡（事务、实例、消费者），状态用 txs 更新
function card(id, x, y, w, label, st = 'run', stText, tagText) {
  put('tx_' + id, 'txc', `<b class="tg">${tagText || id}</b><span class="tl">${label}</span><span class="st ${st}">${stText || ST[st]}</span>`, x, y, w, 40);
}
function cardLabel(id, label) { const e = $('tx_' + id); if (e) { const l = e.querySelector('.tl'); l.innerHTML = label; flash(l); } }
function txs(id, st, stText) { const e = $('tx_' + id); if (!e) return; const s = e.querySelector('.st'); s.className = 'st ' + st; s.innerHTML = stText || ST[st]; flash(s); }
function code(text, y = 182, h) {
  const html = esc(text).replace(/(SELECT|INSERT INTO|INSERT IGNORE|UPDATE|SET|WHERE|AND|FOR UPDATE|FOR SHARE|ON DUPLICATE KEY UPDATE|VALUES|FROM|JOIN|IS NULL|CASE WHEN|THEN|ELSE|END|LIMIT|ORDER BY)\b/g, '<span class="kw">$1</span>').replace(/(-- .*)/g, '<span class="cm">$1</span>');
  put('code', 'code', `<div class="lb">${I18N.exec}</div>${html}`, M.x, y, M.w, h);
}
function gate(title, items, y = 276) {
  put('gate', 'gate', `<div class="gh"><span class="dm"></span>${title}</div>` + items.map(([t, o], i) => `<div class="gi" id="gi${i}"><span class="mk">·</span><span>${t}</span><span class="oc">${o || ''}</span></div>`).join(''), M.x, y, M.w);
}
function check(i, st, oc) {
  const e = $('gi' + i); if (!e) return; e.className = 'gi ' + st;
  e.querySelector('.mk').textContent = st === 'ok' ? '✓' : st === 'no' ? '✗' : st === 'go' ? '➜' : '·';
  if (oc != null) e.querySelector('.oc').innerHTML = oc; flash(e);
}
function note(title, body, y = 412, h) { put('note', 'note', `<div class="nt">${title}</div>${body}`, M.x, y, M.w, h); }
function clearApp(...keep) { ['code', 'gate', 'note', ...[...stage.querySelectorAll('.txc')].map(e => e.id)].forEach(id => { if (!keep.includes(id)) gone(id); }); }

/* ---- 浏览器栏 ---- */
function feInit() {
  put('fe_br', 'fe', `<div class="url">🔒 /products/7</div><div class="pd"><div class="pt">${I18N.product}</div><div class="ps">${I18N.spec}</div><div class="pp">¥9.90</div></div><div id="fe_btn" class="btn">${I18N.buy}</div>`, 26, 88, 248, 170);
  put('fe_ss', 'fe ss', `<div class="lb">sessionStorage</div><div class="k">aiplatform.request-key.purchase:7</div><div class="v" id="fe_key">${I18N.empty}</div>`, 26, 266, 248, 70);
}
function feKey(v) { const e = $('fe_key'); e.innerHTML = v; flash(e); }
function feBtn(text, cls = '') { const b = $('fe_btn'); b.innerHTML = text; b.className = 'btn ' + cls; }
function press() { const b = $('fe_btn'); if (S.instant || !b) return; b.classList.add('press'); setTimeout(() => b.classList.remove('press'), 260); }
function feSt(html, tone = '') { put('fe_st', 'fe st ' + tone, html, 26, 344, 248, null); }
function feUrl(u) { const e = $('fe_br'); if (e) e.querySelector('.url').innerHTML = '🔒 ' + u; }

function clock(v, jump) { const e = put('clock', 'clock' + (jump ? ' jump' : ''), `${I18N.clock} NOW(3) = ${v}`, 960, 58, 296, 22); if (jump) later(900, () => e.classList.remove('jump')); }
function tagAt(id, text, x, y, cls = '') { put(id, 'tag ' + cls, text, x, y); }
function ov(html) { put('ov', '', `<div class="oc-card">${html}</div>`, 0, 0, 1280, 590); }
function ovOff() { gone('ov'); }


/* ---- 静态框架：由每集的 EP 配置决定标题和三栏 ---- */
let EP = { title: '', example: '', lanes: [['', ''], ['', ''], ['', '']] };
function setEpisode(cfg) { EP = cfg; document.title = cfg.docTitle || cfg.title; }
function frame() {
  stage.innerHTML = '';
  for (const k in T) delete T[k];
  put('hdr', '', `<span class="ep">${EP.title}</span><span class="ch" id="chName"></span><span class="sp"></span><span class="ex">${EP.example}</span>`, 16, 10, 1248, 36);
  const L = EP.lanes;
  // 栏位：[x, 宽度]，缺省为 268 / 404 / 552 的三栏
  const C = EP.cols || [[16, 268], [296, 404], [712, 552]];
  M.x = C[1][0] + 10; M.w = C[1][1] - 20;
  if (!EP.noLanes) {
  put('ln_fe', 'lane', `<div class="lh"><b>${L[0][0]}</b>${L[0][1]}</div>`, C[0][0], 52, C[0][1], 534);
  put('ln_app', 'lane', `<div class="lh"><b>${L[1][0]}</b>${L[1][1]}</div>`, C[1][0], 52, C[1][1], 534);
  put('ln_db', 'lane', `<div class="lh"><b>${L[2][0]}</b>${L[2][1]}</div>`, C[2][0], 52, C[2][1], 534);
  }
  put('cap', '', '', 16, 596, 1248, 112);
}
// 第二个讲解卡（与 note 并存）
function note2(title, body, y, x = M.x, w = M.w) { put('note2', 'note', `<div class="nt">${title}</div>${body}`, x, y, w); }

/* ---- 剧本结构 ---- */
const scenes = [];
function scene(title, steps) { scenes.push({ title, steps }); }
function step(cap, ...acts) { const a = []; for (let i = 0; i < acts.length; i += 2) a.push([acts[i], acts[i + 1]]); return { cap, acts: a }; }

/* ============ 播放器 ============ */
function startPlayer() {

const steps = [];
scenes.forEach((s, si) => s.steps.forEach((st, k) => steps.push({ ...st, si, k })));
let cur = -1, playing = false, advTimer = null;

function stepDur(st) {
  const last = st.acts.length ? st.acts[st.acts.length - 1][0] : 0;
  const plain = st.cap.replace(/<[^>]+>/g, '');
  return Math.max(last + 2200, plain.length * 150 + 2000);
}
function runActs(st) {
  if (S.instant) { st.acts.forEach(([, fn]) => fn()); return; }
  st.acts.forEach(([t, fn]) => later(t, fn));
}
function go(i) {
  i = Math.max(0, Math.min(steps.length - 1, i));
  S.timers.forEach(clearTimeout); S.timers = []; clearTimeout(advTimer);
  S.instant = true; stage.classList.add('instant');
  frame();
  for (let k = 0; k < i; k++) runActs(steps[k]);
  void stage.offsetHeight;
  stage.classList.remove('instant'); S.instant = false;
  cur = i; const st = steps[i];
  $('cap').innerHTML = st.cap;
  $('chName').textContent = scenes[st.si].title;
  runActs(st);
  ui();
  if (playing) schedule();
}
function schedule() {
  clearTimeout(advTimer);
  advTimer = setTimeout(() => {
    if (cur < steps.length - 1) go(cur + 1);
    else { playing = false; ui(); document.title = 'DONE'; }
  }, stepDur(steps[cur]) / S.speed);
}
function setPlay(p) { playing = p; if (p) { if (cur >= steps.length - 1) go(0); else schedule(); } else clearTimeout(advTimer); ui(); }

/* 控制条 */
const bar = $('bar'), sCh = $('sCh');
scenes.forEach((s, si) => {
  const seg = document.createElement('div'); seg.className = 'seg'; seg.title = s.title; seg.style.flex = s.steps.length;
  seg.innerHTML = '<div class="f"></div>'; seg.onclick = () => go(steps.findIndex(x => x.si === si)); bar.appendChild(seg);
  const o = document.createElement('option'); o.value = si; o.textContent = s.title; sCh.appendChild(o);
});
sCh.onchange = () => go(steps.findIndex(x => x.si === +sCh.value));
function ui() {
  $('bPlay').textContent = playing ? '⏸' : '▶';
  $('pos').textContent = `${cur + 1} / ${steps.length}`;
  if (cur >= 0) sCh.value = steps[cur].si;
  [...bar.children].forEach((seg, si) => {
    const idx = steps.filter(x => x.si === si); const first = steps.indexOf(idx[0]);
    const done = cur < first ? 0 : Math.min(idx.length, cur - first + 1);
    seg.firstChild.style.width = (done / idx.length * 100) + '%';
  });
}
$('bPrev').onclick = () => go(cur - 1);
// 语言切换与目录：EP.alt 为另一语言的同集页面，EP.home 为目录页
$('bLang').style.display = EP.alt ? '' : 'none';
$('bLang').onclick = () => { location.href = EP.alt + '?step=' + (cur + 1); };
$('bHome').style.display = EP.home ? '' : 'none';
$('bHome').onclick = () => { location.href = EP.home; };
$('bNext').onclick = () => go(cur + 1);
$('bPlay').onclick = () => setPlay(!playing);
$('sSp').onchange = e => { S.speed = +e.target.value; if (playing) schedule(); };
document.addEventListener('keydown', e => {
  if (e.key === 'ArrowRight') go(cur + 1);
  else if (e.key === 'ArrowLeft') go(cur - 1);
  else if (e.key === ' ') { e.preventDefault(); setPlay(!playing); }
});

/* 缩放适配 */
function fit() {
  const w = $('wrap'); const sc = Math.min(w.clientWidth / 1280, w.clientHeight / 720);
  stage.style.transform = `scale(${sc})`;
}
window.addEventListener('resize', fit);

const qs = new URLSearchParams(location.search);
if (qs.has('record')) document.body.classList.add('rec');
fit();
go(qs.has('step') ? +qs.get('step') - 1 : 0);
if (qs.has('record') || qs.has('autoplay')) setTimeout(() => setPlay(true), 800);
}

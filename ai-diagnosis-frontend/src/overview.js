import { railHtml } from './rail.js';
import './overview.css';

const TYPE_LABELS = {
  HIGH_CPU: '高 CPU',
  SLOW_REQUEST: '接口慢',
  THREAD_BLOCKED: '线程阻塞',
  MEMORY_ABNORMAL: '内存异常',
  UNKNOWN: '待确认'
};

const TYPE_TONES = {
  HIGH_CPU: 'red',
  SLOW_REQUEST: 'amber',
  THREAD_BLOCKED: 'violet',
  MEMORY_ABNORMAL: 'cyan',
  UNKNOWN: 'neutral'
};

const STATUS_META = {
  CREATED: { label: '待启动', tone: 'neutral', icon: 'ph-circle' },
  RUNNING: { label: '诊断中', tone: 'amber', icon: 'ph-spinner-gap' },
  INTERRUPTED: { label: '已中断', tone: 'red', icon: 'ph-plugs' },
  FINISHED: { label: '已完成', tone: 'green', icon: 'ph-check-circle' },
  FAILED: { label: '失败', tone: 'red', icon: 'ph-warning' }
};

const ENV_LABELS = {
  dev: '开发',
  test: '测试',
  uat: '验收',
  staging: '预发',
  prod: '生产'
};

const TIME_RANGES = [
  { days: 1, label: '今天' },
  { days: 7, label: '近 7 天' },
  { days: 30, label: '近 30 天' }
];

const PAGE_SIZE = 20;
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export function mountOverview(container, { apiBase, query = '' }) {
  const params = parseQuery(query);
  const local = {
    apiBase,
    filters: {
      appId: params.appId || '',
      env: params.env || '',
      diagnoseType: params.diagnoseType || '',
      status: params.status || '',
      keyword: params.keyword || ''
    },
    page: Math.max(1, Number(params.page) || 1),
    rangeDays: Math.min(Number(params.range) || 7, 90) || 7,
    instanceOptions: [],
    stats: null,
    statsError: '',
    list: [],
    total: 0,
    loadingList: false,
    loadingStats: false,
    listError: ''
  };

  container.innerHTML = overviewShell(local);
  bindOverview(container, local);
  loadOptions(local);
  loadStats(local);
  loadList(local, true);
  runReveal(container);
}

function overviewShell(local) {
  return `
    <div class="diagnosis-studio">
      ${railHtml('overview')}
      <div class="workspace overview-workspace">
        <header class="topbar overview-topbar reveal">
          <div class="title-block">
            <span>AI + Arthas</span>
            <h1>事件概览</h1>
            <p>已诊断事件的记录与回顾</p>
          </div>
          <div class="overview-toolbar">
            <div class="range-switch" role="group" aria-label="统计时间范围">
              ${TIME_RANGES.map((range) => `
                <button type="button" class="range-chip${range.days === local.rangeDays ? ' is-active' : ''}" data-range="${range.days}">${range.label}</button>
              `).join('')}
            </div>
            <button type="button" class="secondary-button" data-refresh>
              <i class="ph ph-arrow-clockwise" aria-hidden="true"></i>刷新
            </button>
            <button type="button" class="primary-button quick-diagnose" data-route="/studio" data-new-diagnosis title="启动新的因果诊断">
              <i class="ph ph-stethoscope" aria-hidden="true"></i>AI 快速诊断
            </button>
          </div>
        </header>

        <section class="overview-stats reveal" aria-label="统计概览" data-stats-root>
          ${renderStatsSkeleton()}
        </section>

        <section class="overview-filters reveal" aria-label="筛选条件">
          ${renderFilters(local)}
        </section>

        <section class="overview-list reveal" aria-label="诊断事件列表">
          <div class="list-head">
            <p class="panel-label">诊断事件</p>
            <span class="list-count" data-list-count></span>
          </div>
          <div class="event-cards" data-event-cards>
            ${renderListSkeleton()}
          </div>
          <div class="list-footer" data-list-footer hidden></div>
        </section>
      </div>
    </div>
  `;
}

function renderStatsSkeleton() {
  return `
    <div class="stats-grid">
      ${Array.from({ length: 4 }).map(() => `
        <div class="stat-card stat-skeleton">
          <span class="stat-value-skeleton"></span>
          <span class="stat-label-skeleton"></span>
        </div>
      `).join('')}
      <div class="stat-card stat-card-wide stat-skeleton">
        <span class="stat-label-skeleton"></span>
        <span class="sparkline-skeleton"></span>
      </div>
    </div>
  `;
}

function renderStats(local) {
  const stats = local.stats;
  if (!stats) return '';
  const success = stats.successRate ?? 0;
  const trend = stats.dailyTrend || [];
  const typeEntries = Object.entries(stats.typeCounts || {});
  const typeMax = Math.max(1, ...typeEntries.map(([, count]) => count));

  return `
    <div class="stats-grid">
      <div class="stat-card" data-tone="blue">
        <span class="stat-value">${stats.total}</span>
        <span class="stat-label">事件总数</span>
      </div>
      <div class="stat-card" data-tone="green">
        <span class="stat-value">${success.toFixed(1)}<small>%</small></span>
        <span class="stat-label">成功率（已完成 / 已结束）</span>
      </div>
      <div class="stat-card" data-tone="amber">
        <span class="stat-value">${stats.running}</span>
        <span class="stat-label">诊断中</span>
      </div>
      <div class="stat-card" data-tone="red">
        <span class="stat-value">${stats.failed}</span>
        <span class="stat-label">失败</span>
      </div>
      <div class="stat-card stat-card-wide">
        <div class="stat-card-head">
          <span class="stat-label">近 ${local.rangeDays} 天趋势</span>
          <span class="stat-sub">${trend.reduce((sum, day) => sum + day.count, 0)} 次诊断</span>
        </div>
        ${renderSparkline(trend)}
      </div>
      <div class="stat-card stat-card-wide">
        <div class="stat-card-head">
          <span class="stat-label">类型分布</span>
        </div>
        ${typeEntries.length
          ? `<ul class="type-bars">${typeEntries.map(([type, count]) => `
              <li>
                <span class="type-bar-label">${escapeHtml(TYPE_LABELS[type] || type)}</span>
                <span class="type-bar-track"><span class="type-bar-fill" data-tone="${TYPE_TONES[type] || 'neutral'}" style="width:${Math.round((count / typeMax) * 100)}%"></span></span>
                <span class="type-bar-count">${count}</span>
              </li>`).join('')}</ul>`
          : `<p class="stat-empty">暂无类型数据</p>`}
      </div>
    </div>
  `;
}

function renderSparkline(trend) {
  if (!trend || !trend.length) {
    return `<p class="stat-empty">暂无趋势数据</p>`;
  }
  const width = 320;
  const height = 64;
  const pad = 4;
  const values = trend.map((day) => day.count);
  const max = Math.max(1, ...values);
  const stepX = (width - pad * 2) / Math.max(1, values.length - 1);
  const points = values.map((value, index) => {
    const x = pad + index * stepX;
    const y = height - pad - (value / max) * (height - pad * 2);
    return [x, y];
  });
  const linePath = points.map(([x, y], index) => `${index === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
  const areaPath = `${linePath} L${points[points.length - 1][0].toFixed(1)} ${height - pad} L${points[0][0].toFixed(1)} ${height - pad} Z`;
  const lastPoint = points[points.length - 1];
  const lastLabel = trend[trend.length - 1];

  return `
    <svg class="sparkline" viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" role="img" aria-label="每日诊断数量趋势">
      <defs>
        <linearGradient id="sparkFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="rgb(52 125 255 / 0.45)"></stop>
          <stop offset="100%" stop-color="rgb(52 125 255 / 0)"></stop>
        </linearGradient>
      </defs>
      <path d="${areaPath}" fill="url(#sparkFill)"></path>
      <path d="${linePath}" fill="none" stroke="rgb(52 125 255)" stroke-width="1.6" stroke-linejoin="round" stroke-linecap="round"></path>
      ${lastPoint ? `<circle cx="${lastPoint[0].toFixed(1)}" cy="${lastPoint[1].toFixed(1)}" r="2.6" fill="rgb(120 170 255)"></circle>` : ''}
    </svg>
    <div class="sparkline-foot">
      <span>${trend[0].date.slice(5)}</span>
      <span>最新 ${lastLabel.count} 次</span>
      <span>${lastLabel.date.slice(5)}</span>
    </div>
  `;
}

function renderFilters(local) {
  return `
    <div class="filter-row">
      <label class="filter-field">
        <span>服务</span>
        <select data-filter="appId" aria-label="按服务筛选">
          <option value="">全部服务</option>
        </select>
      </label>
      <label class="filter-field">
        <span>环境</span>
        <select data-filter="env" aria-label="按环境筛选">
          <option value="">全部环境</option>
        </select>
      </label>
      <label class="filter-field">
        <span>类型</span>
        <select data-filter="diagnoseType" aria-label="按诊断类型筛选">
          <option value="">全部类型</option>
          ${Object.keys(TYPE_LABELS).map((type) => `
            <option value="${type}"${type === local.filters.diagnoseType ? ' selected' : ''}>${TYPE_LABELS[type]}</option>
          `).join('')}
        </select>
      </label>
      <label class="filter-field">
        <span>状态</span>
        <select data-filter="status" aria-label="按状态筛选">
          <option value="">全部状态</option>
          ${Object.keys(STATUS_META).map((status) => `
            <option value="${status}"${status === local.filters.status ? ' selected' : ''}>${STATUS_META[status].label}</option>
          `).join('')}
        </select>
      </label>
      <label class="filter-field filter-keyword">
        <span>关键词</span>
        <input type="search" data-filter="keyword" value="${escapeHtml(local.filters.keyword)}" placeholder="任务编号或问题描述" autocomplete="off" />
      </label>
    </div>
  `;
}

function populateFilterOptions(local) {
  const appIdSelect = document.querySelector('[data-filter="appId"]');
  const envSelect = document.querySelector('[data-filter="env"]');
  if (!appIdSelect || !envSelect) return;

  const appIds = [...new Set(local.instanceOptions.map((option) => option.appId))];
  const envs = [...new Set(local.instanceOptions.map((option) => option.env))];
  appIdSelect.innerHTML = `<option value="">全部服务</option>` + appIds.map((appId) =>
    `<option value="${escapeHtml(appId)}"${appId === local.filters.appId ? ' selected' : ''}>${escapeHtml(appId)}</option>`).join('');
  envSelect.innerHTML = `<option value="">全部环境</option>` + envs.map((env) =>
    `<option value="${escapeHtml(env)}"${env === local.filters.env ? ' selected' : ''}>${escapeHtml(ENV_LABELS[env] || env)}</option>`).join('');
}

function renderListSkeleton() {
  return Array.from({ length: 4 }).map(() => `
    <article class="event-card event-card-skeleton">
      <div class="event-card-head">
        <span class="skeleton-pill"></span>
        <span class="skeleton-line w-40"></span>
      </div>
      <span class="skeleton-line w-80"></span>
      <span class="skeleton-line w-60"></span>
      <div class="event-card-foot"><span class="skeleton-line w-30"></span></div>
    </article>
  `).join('');
}

function renderCards(local) {
  if (local.listError) {
    return `
      <div class="list-state list-state-error">
        <i class="ph ph-plugs" aria-hidden="true"></i>
        <p>${escapeHtml(local.listError)}</p>
        <button type="button" class="secondary-button" data-retry-list>重试</button>
      </div>
    `;
  }

  if (!local.loadingList && local.list.length === 0) {
    return `
      <div class="list-state list-state-empty">
        <i class="ph ph-stack" aria-hidden="true"></i>
        <p>暂无符合条件的诊断事件。</p>
        <button type="button" class="primary-button" data-route="/studio" data-new-diagnosis>前往诊断现场</button>
      </div>
    `;
  }

  return local.list.map((item) => eventCardHtml(item)).join('');
}

function eventCardHtml(item) {
  const status = STATUS_META[item.status] || STATUS_META.CREATED;
  const type = TYPE_LABELS[item.diagnoseType] || TYPE_LABELS.UNKNOWN;
  const typeTone = TYPE_TONES[item.diagnoseType] || 'neutral';
  const envLabel = ENV_LABELS[item.env] || item.env || '';
  const conclusion = item.conclusion || (item.status === 'FAILED' || item.status === 'INTERRUPTED' ? item.errorMessage : '');
  const isRunning = item.status === 'RUNNING' || item.status === 'CREATED';
  const isInterrupted = item.status === 'INTERRUPTED';
  const canDelete = item.status === 'FINISHED' || item.status === 'FAILED' || isInterrupted;

  return `
    <article class="event-card" data-tone="${status.tone}" data-task-no="${escapeHtml(item.taskNo)}" tabindex="0" role="button" aria-label="查看事件 ${escapeHtml(item.taskNo)}">
      <header class="event-card-head">
        <span class="event-status" data-tone="${status.tone}">
          <i class="ph ${status.icon}" aria-hidden="true"></i>${status.label}
        </span>
        <span class="event-type" data-tone="${typeTone}">${escapeHtml(type)}</span>
        <span class="event-app">${escapeHtml(item.appId || '-')}${envLabel ? ` · ${escapeHtml(envLabel)}` : ''}</span>
        <span class="event-task-no">${escapeHtml(item.taskNo)}</span>
      </header>
      <p class="event-question">${escapeHtml(truncate(item.question, 120))}</p>
      ${conclusion ? `<p class="event-conclusion"><i class="ph ph-brain" aria-hidden="true"></i>${escapeHtml(truncate(conclusion, 160))}</p>` : ''}
      <footer class="event-card-foot">
        <span><i class="ph ph-clock" aria-hidden="true"></i>${escapeHtml(formatDateTime(item.createdAt))}</span>
        ${item.targetUri ? `<span><i class="ph ph-globe" aria-hidden="true"></i>${escapeHtml(truncate(item.targetUri, 40))}</span>` : ''}
        ${isRunning || isInterrupted
          ? `<button type="button" class="event-resume" data-resume="${escapeHtml(item.taskNo)}">${isInterrupted ? '重新诊断' : '接续观察'}<i class="ph ph-arrow-right" aria-hidden="true"></i></button>`
          : `<span class="event-open"><i class="ph ph-caret-right" aria-hidden="true"></i>查看详情</span>`}
        ${canDelete ? `<button type="button" class="event-delete" data-delete="${escapeHtml(item.taskNo)}" title="删除该诊断事件" aria-label="删除事件 ${escapeHtml(item.taskNo)}"><i class="ph ph-trash" aria-hidden="true"></i></button>` : ''}
      </footer>
    </article>
  `;
}

function renderListFooter(local) {
  const footer = document.querySelector('[data-list-footer]');
  if (!footer) return;
  const loaded = local.list.length;
  const hasMore = loaded < local.total;
  footer.hidden = loaded === 0;
  footer.innerHTML = hasMore
    ? `<button type="button" class="secondary-button load-more" data-load-more ${local.loadingList ? 'disabled' : ''}>${local.loadingList ? '加载中…' : `加载更多（剩余 ${local.total - loaded}）`}</button>`
    : `<span class="list-end">已显示全部 ${local.total} 条</span>`;
}

function bindOverview(container, local) {
  container.querySelectorAll('[data-range]').forEach((button) => {
    button.addEventListener('click', () => {
      local.rangeDays = Number(button.dataset.range);
      syncHash(local);
      container.querySelectorAll('[data-range]').forEach((node) =>
        node.classList.toggle('is-active', node === button));
      loadStats(local);
    });
  });

  const refresh = container.querySelector('[data-refresh]');
  refresh?.addEventListener('click', () => {
    loadStats(local);
    local.page = 1;
    loadList(local, true);
  });

  container.querySelectorAll('[data-filter]').forEach((control) => {
    const field = control.dataset.filter;
    const fire = () => {
      local.filters[field] = control.value.trim();
      local.page = 1;
      syncHash(local);
      loadList(local, true);
    };
    if (control.tagName === 'INPUT') {
      control.addEventListener('input', debounce(fire, 400));
    } else {
      control.addEventListener('change', fire);
    }
  });

  container.addEventListener('click', (event) => {
    const resume = event.target.closest('[data-resume]');
    if (resume) {
      event.stopPropagation();
      location.hash = `#/studio?taskNo=${encodeURIComponent(resume.dataset.resume)}`;
      return;
    }
    const del = event.target.closest('[data-delete]');
    if (del) {
      event.stopPropagation();
      confirmDelete(local, del.dataset.delete);
      return;
    }
    const card = event.target.closest('[data-task-no]');
    if (card) {
      location.hash = `#/overview/${card.dataset.taskNo}`;
    }
    const retry = event.target.closest('[data-retry-list]');
    if (retry) loadList(local, true);
    const more = event.target.closest('[data-load-more]');
    if (more) {
      local.page += 1;
      loadList(local, false);
    }
  });

  container.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    const card = event.target.closest('[data-task-no]');
    if (card) {
      event.preventDefault();
      location.hash = `#/overview/${card.dataset.taskNo}`;
    }
  });
}

async function loadOptions(local) {
  try {
    const options = await requestJson(`${local.apiBase}/api/app-instances/options`);
    local.instanceOptions = Array.isArray(options) ? options.filter((option) => option?.appId && option?.env) : [];
  } catch {
    local.instanceOptions = [];
  }
  populateFilterOptions(local);
}

async function loadStats(local) {
  local.loadingStats = true;
  local.statsError = '';
  const root = document.querySelector('[data-stats-root]');
  try {
    local.stats = await requestJson(`${local.apiBase}/api/diagnose/tasks/stats?days=${local.rangeDays}`);
    if (root) {
      root.innerHTML = renderStats(local);
      runReveal(root);
    }
  } catch (error) {
    local.statsError = error.message || '统计加载失败';
    if (root) {
      root.innerHTML = `<div class="list-state list-state-error"><i class="ph ph-warning" aria-hidden="true"></i><p>${escapeHtml(local.statsError)}</p></div>`;
    }
  } finally {
    local.loadingStats = false;
  }
}

async function loadList(local, replace) {
  local.loadingList = true;
  local.listError = '';
  const cards = document.querySelector('[data-event-cards]');
  const count = document.querySelector('[data-list-count]');

  if (replace && cards) {
    cards.innerHTML = renderListSkeleton();
  }

  const params = new URLSearchParams({ pageNo: local.page, pageSize: PAGE_SIZE });
  Object.entries(local.filters).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });

  try {
    const page = await requestJson(`${local.apiBase}/api/diagnose/tasks?${params.toString()}`);
    const items = page?.list || [];
    local.list = replace ? items : [...local.list, ...items];
    local.total = page?.total ?? local.list.length;
    if (count) count.textContent = `共 ${local.total} 条`;
    if (cards) cards.innerHTML = renderCards(local);
    renderListFooter(local);
    if (!replace) runReveal(cards);
  } catch (error) {
    local.listError = error.message || '事件列表加载失败';
    local.list = [];
    local.total = 0;
    if (count) count.textContent = '';
    if (cards) cards.innerHTML = renderCards(local);
    const footer = document.querySelector('[data-list-footer]');
    if (footer) footer.hidden = true;
  } finally {
    local.loadingList = false;
    renderListFooter(local);
  }
}

function confirmDelete(local, taskNo) {
  if (document.querySelector('.delete-confirm')) return;

  const overlay = document.createElement('div');
  overlay.className = 'delete-confirm';
  overlay.innerHTML = `
    <div class="delete-confirm-card" role="dialog" aria-modal="true" aria-label="确认删除诊断事件">
      <span class="delete-confirm-icon"><i class="ph ph-warning" aria-hidden="true"></i></span>
      <h3>删除该诊断事件？</h3>
      <p>事件 <strong>${escapeHtml(taskNo)}</strong> 及其采样命令、诊断报告将被永久删除，且无法恢复。</p>
      <div class="delete-confirm-actions">
        <button type="button" class="ghost-button" data-cancel>取消</button>
        <button type="button" class="primary-button delete-confirm-submit" data-confirm>
          <i class="ph ph-trash" aria-hidden="true"></i>确认删除
        </button>
      </div>
    </div>
  `;
  document.body.appendChild(overlay);

  const close = () => overlay.remove();
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay || event.target.closest('[data-cancel]')) close();
  });
  const esc = (event) => { if (event.key === 'Escape') { close(); document.removeEventListener('keydown', esc); } };
  document.addEventListener('keydown', esc);

  overlay.querySelector('[data-confirm]').addEventListener('click', async () => {
    const submit = overlay.querySelector('[data-confirm]');
    submit.disabled = true;
    submit.innerHTML = '<i class="ph ph-spinner-gap" aria-hidden="true"></i>删除中…';
    const ok = await deleteEvent(local, taskNo);
    if (ok) close();
    else {
      submit.disabled = false;
      submit.innerHTML = '<i class="ph ph-trash" aria-hidden="true"></i>确认删除';
    }
  });
}

async function deleteEvent(local, taskNo) {
  try {
    const response = await fetch(`${local.apiBase}/api/diagnose/tasks/${encodeURIComponent(taskNo)}`, {
      method: 'DELETE'
    });
    if (!response.ok) {
      const payload = await response.text();
      const message = safeJsonParse(payload)?.message || `删除失败（HTTP ${response.status}）`;
      throw new Error(message);
    }
    loadStats(local);
    loadList(local, true);
    return true;
  } catch (error) {
    showToast(error.message || '删除失败');
    return false;
  }
}

let toastTimer = null;
function showToast(message) {
  let toast = document.querySelector('.overview-toast');
  if (!toast) {
    toast = document.createElement('div');
    toast.className = 'overview-toast';
    document.body.appendChild(toast);
  }
  toast.textContent = message;
  toast.classList.add('is-visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('is-visible'), 3200);
}

function syncHash(local) {
  const params = new URLSearchParams();
  if (local.rangeDays && local.rangeDays !== 7) params.set('range', local.rangeDays);
  Object.entries(local.filters).forEach(([key, value]) => {
    if (value) params.set(key, value);
  });
  if (local.page > 1) params.set('page', local.page);
  const qs = params.toString();
  const next = qs ? `#/overview?${qs}` : '#/overview';
  if (location.hash !== next) {
    history.replaceState(null, '', next);
  }
}

function runReveal(scope) {
  if (reduceMotion) {
    scope.querySelectorAll('.reveal').forEach((node) => node.classList.add('is-visible'));
    return;
  }
  const targets = scope.querySelectorAll('.reveal:not(.is-visible)');
  targets.forEach((node, index) => {
    node.style.opacity = '0';
    node.style.transform = 'translateY(12px)';
    requestAnimationFrame(() => {
      node.style.transition = 'opacity 520ms cubic-bezier(0.16,1,0.3,1), transform 520ms cubic-bezier(0.16,1,0.3,1)';
      node.style.transitionDelay = `${index * 70}ms`;
      node.style.opacity = '1';
      node.style.transform = 'translateY(0)';
      node.classList.add('is-visible');
    });
  });
}

function parseQuery(query) {
  const params = {};
  new URLSearchParams(query).forEach((value, key) => { params[key] = value; });
  return params;
}

function debounce(fn, wait) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  };
}

function truncate(value, max) {
  const text = String(value || '').replace(/\s+/g, ' ').trim();
  if (!text) return '';
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 19);
  return date.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-');
}

async function requestJson(url) {
  const response = await fetch(url);
  const text = await response.text();
  const payload = text ? safeJsonParse(text) : {};
  if (!response.ok) {
    throw new Error(payload.message || `HTTP ${response.status}`);
  }
  return payload;
}

function safeJsonParse(text) {
  try { return JSON.parse(text); } catch { return { raw: text }; }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

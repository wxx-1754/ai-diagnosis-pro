import { railHtml } from './rail.js';
import { extractInsightSummary, createEmptyInsightSummary } from './insight-summary.js';
import './overview.css';

const TYPE_LABELS = {
  HIGH_CPU: '高 CPU',
  SLOW_REQUEST: '接口慢',
  THREAD_BLOCKED: '线程阻塞',
  MEMORY_ABNORMAL: '内存异常',
  UNKNOWN: '待确认'
};

const STATUS_META = {
  CREATED: { label: '待启动', tone: 'neutral', icon: 'ph-circle' },
  RUNNING: { label: '诊断中', tone: 'amber', icon: 'ph-spinner-gap' },
  INTERRUPTED: { label: '已中断', tone: 'red', icon: 'ph-plugs' },
  FINISHED: { label: '已完成', tone: 'green', icon: 'ph-check-circle' },
  FAILED: { label: '失败', tone: 'red', icon: 'ph-warning' }
};

const ENV_LABELS = {
  dev: '开发', test: '测试', uat: '验收', staging: '预发', prod: '生产'
};

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export function mountEventDetail(container, { taskNo, apiBase }) {
  const local = {
    taskNo,
    apiBase,
    detail: null,
    report: null,
    insight: createEmptyInsightSummary(),
    detailError: '',
    reportError: '',
    reportAvailable: true
  };

  container.innerHTML = detailShell(local);
  bindDetail(container, local);
  // 先加载详情拿到任务状态，再据此决定是否拉取报告（进行中任务无报告，跳过以避免 400）。
  loadDetail(local).then(() => loadReport(local));
  runReveal(container);
}

function detailShell(local) {
  return `
    <div class="diagnosis-studio">
      ${railHtml('overview')}
      <div class="workspace detail-workspace">
        <header class="topbar detail-topbar reveal">
          <button type="button" class="back-button" data-route="/overview" aria-label="返回事件列表">
            <i class="ph ph-arrow-left" aria-hidden="true"></i>
            <span>返回列表</span>
          </button>
          <div class="detail-topbar-right">
            <div class="detail-title">
              <span class="panel-label">事件回顾</span>
              <strong data-task-no-label>${escapeHtml(local.taskNo)}</strong>
            </div>
            <div class="detail-actions">
              <button type="button" class="secondary-button" data-download hidden>
                <i class="ph ph-download-simple" aria-hidden="true"></i>下载报告
              </button>
            </div>
          </div>
        </header>

        <div class="detail-body" data-detail-body>
          ${renderDetailSkeleton()}
        </div>
      </div>
    </div>
  `;
}

function renderDetailSkeleton() {
  return `
    <div class="detail-skeleton">
      ${Array.from({ length: 3 }).map(() => '<span class="skeleton-line w-60"></span><span class="skeleton-line w-40"></span>').join('')}
    </div>
  `;
}

function renderDetail(local) {
  const task = local.detail?.task;
  if (!task) {
    return `
      <div class="list-state list-state-error">
        <i class="ph ph-warning" aria-hidden="true"></i>
        <p>${escapeHtml(local.detailError || '事件详情加载失败')}</p>
        <button type="button" class="secondary-button" data-retry-detail>重试</button>
      </div>
    `;
  }
  const records = local.detail.commandRecords || [];
  const events = local.detail.events || [];
  const status = STATUS_META[task.status] || STATUS_META.CREATED;

  return `
    <section class="detail-meta-card reveal" aria-label="任务信息">
      <div class="meta-grid">
        ${metaItem('服务', task.appId)}
        ${metaItem('环境', ENV_LABELS[task.env] || task.env)}
        ${metaItem('类型', TYPE_LABELS[task.diagnoseType] || TYPE_LABELS.UNKNOWN)}
        ${metaItem('状态', `<span class="event-status" data-tone="${status.tone}"><i class="ph ${status.icon}" aria-hidden="true"></i>${status.label}</span>`, true)}
        ${metaItem('目标 URI', task.targetUri || '-')}
        ${metaItem('目标类', task.targetClass || '-')}
        ${metaItem('目标方法', task.targetMethod || '-')}
        ${metaItem('创建时间', formatDateTime(task.createdAt))}
        ${metaItem('操作人', task.userId || '-')}
      </div>
      ${task.question ? `<div class="meta-question"><span class="panel-label">异常描述</span><p>${escapeHtml(task.question)}</p></div>` : ''}
    </section>

    ${task.status === 'FAILED' && task.errorMessage ? `
      <section class="detail-error-card reveal" aria-label="失败原因">
        <div class="result-section-title"><i class="ph ph-warning-octagon" aria-hidden="true"></i><h3>失败原因</h3></div>
        <p class="error-message">${escapeHtml(task.errorMessage)}</p>
      </section>
    ` : ''}

    <section class="detail-timeline reveal" aria-label="证据回放">
      <div class="plan-head">
        <div>
          <p class="panel-label">证据回放</p>
          <h2>采样与推理时间线</h2>
        </div>
        <div class="plan-meta"><span>${events.length || records.length} 条诊断事件</span></div>
      </div>
      <ol class="replay-track">
        ${events.length
          ? `${events.map((event) => eventReplayNodeHtml(event)).join('')}${renderObservationAction(task)}`
          : renderLegacyReplay(task, records)}
      </ol>
    </section>

    ${renderInsight(local)}

    ${renderReport(local)}
  `;
}

function eventReplayNodeHtml(event) {
  const meta = {
    TASK_CREATED: ['任务创建', 'ph-plugs-connected', 'blue'],
    INTENT_CLASSIFYING: ['识别诊断意图', 'ph-magnifying-glass', 'cyan'],
    INTENT_CLASSIFIED: ['诊断意图确认', 'ph-crosshair', 'cyan'],
    PLAN_CREATED: ['生成执行计划', 'ph-list-checks', 'blue'],
    TOOL_CALL_START: ['开始采样', 'ph-terminal-window', 'amber'],
    TOOL_CALL_SUCCESS: ['采样成功', 'ph-check-circle', 'green'],
    TOOL_CALL_FAILED: ['采样失败', 'ph-warning', 'red'],
    AI_ANALYZING: ['AI 分析', 'ph-brain', 'violet'],
    REPORT_GENERATED: ['报告生成', 'ph-file-text', 'violet'],
    TASK_FINISHED: ['诊断完成', 'ph-seal-check', 'green'],
    TASK_FAILED: ['诊断失败', 'ph-warning-octagon', 'red'],
    TASK_INTERRUPTED: ['诊断中断', 'ph-plugs', 'red']
  }[event.eventType] || [event.eventType || '诊断事件', 'ph-activity', 'blue'];
  const detail = event.command || event.message || '';
  return `
    <li class="replay-node" data-tone="${meta[2]}">
      <span class="replay-marker"><i class="ph ${meta[1]}" aria-hidden="true"></i></span>
      <div class="replay-body">
        <strong>${escapeHtml(meta[0])}</strong>
        <small>${escapeHtml(formatDateTime(event.time))}</small>
        ${detail ? `<p>${escapeHtml(detail)}</p>` : ''}
      </div>
    </li>
  `;
}

function renderLegacyReplay(task, records) {
  return `
    <li class="replay-node" data-tone="blue">
      <span class="replay-marker"><i class="ph ph-plugs-connected" aria-hidden="true"></i></span>
      <div class="replay-body">
        <strong>任务创建</strong>
        <small>${escapeHtml(formatDateTime(task.createdAt))}</small>
      </div>
    </li>
    ${records.map((record) => replayNodeHtml(record)).join('')}
    ${renderTerminalNode(task)}
  `;
}

function renderObservationAction(task) {
  if (task.status === 'INTERRUPTED') {
    return `
      <li class="replay-node" data-tone="red">
        <span class="replay-marker"><i class="ph ph-arrow-clockwise" aria-hidden="true"></i></span>
        <div class="replay-body">
          <strong>可以重新发起诊断</strong>
          <small>新任务会保留本次中断记录</small>
          <button type="button" class="secondary-button replay-resume" data-route="/studio?taskNo=${encodeURIComponent(task.taskNo)}">重新诊断</button>
        </div>
      </li>
    `;
  }
  if (task.status === 'RUNNING' || task.status === 'CREATED') {
    return `
      <li class="replay-node" data-tone="amber">
        <span class="replay-marker"><i class="ph ph-spinner-gap" aria-hidden="true"></i></span>
        <div class="replay-body">
          <strong>诊断进行中</strong>
          <small>前往诊断现场观察后续实时事件</small>
          <button type="button" class="secondary-button replay-resume" data-route="/studio?taskNo=${encodeURIComponent(task.taskNo)}">接续观察</button>
        </div>
      </li>
    `;
  }
  return '';
}

function renderTerminalNode(task) {
  if (task.status === 'INTERRUPTED') {
    return `
      <li class="replay-node" data-tone="red">
        <span class="replay-marker"><i class="ph ph-plugs" aria-hidden="true"></i></span>
        <div class="replay-body">
          <strong>诊断已中断</strong>
          <small>${escapeHtml(task.errorMessage || '后台执行已不存在')}</small>
          <button type="button" class="secondary-button replay-resume" data-route="/studio?taskNo=${encodeURIComponent(task.taskNo)}">重新诊断</button>
        </div>
      </li>
    `;
  }
  if (task.status === 'FAILED') {
    return `
      <li class="replay-node" data-tone="red">
        <span class="replay-marker"><i class="ph ph-warning-octagon" aria-hidden="true"></i></span>
        <div class="replay-body">
          <strong>诊断失败</strong>
          <small>流程在此终止</small>
        </div>
      </li>
    `;
  }
  if (task.status === 'FINISHED') {
    return `
      <li class="replay-node" data-tone="green">
        <span class="replay-marker"><i class="ph ph-seal-check" aria-hidden="true"></i></span>
        <div class="replay-body">
          <strong>诊断完成</strong>
          <small>${escapeHtml(formatDateTime(task.updatedAt))}</small>
        </div>
      </li>
    `;
  }
  return `
    <li class="replay-node" data-tone="amber">
      <span class="replay-marker"><i class="ph ph-spinner-gap" aria-hidden="true"></i></span>
      <div class="replay-body">
        <strong>诊断进行中</strong>
        <small>可在诊断现场接续观察实时事件</small>
        <button type="button" class="secondary-button replay-resume" data-route="/studio?taskNo=${encodeURIComponent(task.taskNo)}">接续观察</button>
      </div>
    </li>
  `;
}

function replayNodeHtml(record) {
  const tone = record.success === false ? 'red' : 'green';
  const icon = record.success === false ? 'ph-warning' : 'ph-check-circle';
  const cost = Number.isFinite(Number(record.costMillis)) ? `${record.costMillis}ms` : '';
  const excerpt = record.outputExcerpt || record.errorMessage || '';
  return `
    <li class="replay-node" data-tone="${tone}">
      <span class="replay-marker"><i class="ph ${icon}" aria-hidden="true"></i></span>
      <div class="replay-body">
        <div class="replay-head">
          <strong>${escapeHtml(record.command || record.commandType || 'Arthas 命令')}</strong>
          ${cost ? `<span class="replay-cost">${cost}</span>` : ''}
        </div>
        ${record.commandType ? `<small class="replay-type">${escapeHtml(record.commandType)}</small>` : ''}
        ${excerpt ? `<p class="replay-output">${escapeHtml(truncate(excerpt, 220))}</p>` : ''}
      </div>
    </li>
  `;
}

function renderInsight(local) {
  if (local.reportError || !local.report) return '';
  const insight = local.insight;
  if (!insight.rootCause && !insight.expectedEffect && insight.recommendedActions.length === 0) {
    return '';
  }
  return `
    <section class="detail-insight reveal" aria-label="根因与建议">
      <div class="plan-head">
        <div>
          <p class="panel-label">根因与建议</p>
          <h2>AI 提炼结论</h2>
        </div>
      </div>
      <div class="insight-grid">
        <div class="result-section root-analysis">
          <div class="result-section-title"><i class="ph ph-brain" aria-hidden="true"></i><h3>根因分析</h3></div>
          <strong class="root-conclusion">${escapeHtml(insight.rootCause || '未提炼到明确根因')}</strong>
        </div>
        <div class="result-section effect-section">
          <div class="result-section-title"><i class="ph ph-trend-down" aria-hidden="true"></i><h3>预期效果</h3></div>
          <p>${escapeHtml(insight.expectedEffect || '暂无可靠估算，需在修复后验证。')}</p>
        </div>
        <div class="result-section action-section">
          <div class="result-section-title"><i class="ph ph-terminal-window" aria-hidden="true"></i><h3>推荐操作</h3></div>
          <ol class="action-list">
            ${(insight.recommendedActions.length
              ? insight.recommendedActions
              : ['等待诊断完成后生成详细操作。'])
              .map((action) => `<li>${escapeHtml(action)}</li>`).join('')}
          </ol>
        </div>
      </div>
    </section>
  `;
}

function renderReport(local) {
  if (local.reportError) return '';
  if (!local.report) {
    return `
      <section class="detail-report reveal" aria-label="完整报告">
        <div class="plan-head"><div><p class="panel-label">完整报告</p><h2>诊断报告</h2></div></div>
        <div class="list-state"><i class="ph ph-file-text" aria-hidden="true"></i><p>该事件暂未生成报告。</p></div>
      </section>
    `;
  }
  const markdown = local.report.reportMarkdown || '';
  return `
    <section class="detail-report reveal" aria-label="完整报告">
      <div class="plan-head">
        <div>
          <p class="panel-label">完整报告</p>
          <h2>${escapeHtml(local.report.reportTitle || '诊断报告')}</h2>
        </div>
        <div class="plan-meta">
          ${local.report.aiModel ? `<span>模型 ${escapeHtml(local.report.aiModel)}</span>` : ''}
        </div>
      </div>
      <pre class="report-pre">${escapeHtml(markdown)}</pre>
    </section>
  `;
}

function metaItem(label, value, raw = false) {
  const content = raw ? (value || '-') : escapeHtml(value || '-');
  return `
    <div class="meta-item">
      <span class="panel-label">${escapeHtml(label)}</span>
      <span class="meta-value">${content}</span>
    </div>
  `;
}

function bindDetail(container, local) {
  container.addEventListener('click', (event) => {
    if (event.target.closest('[data-retry-detail]')) {
      loadDetail(local);
    }
    if (event.target.closest('[data-download]') && local.report?.reportMarkdown) {
      downloadMarkdown(local.taskNo, local.report.reportMarkdown);
    }
  });
}

// 只有 FINISHED 任务会生成报告，其余状态直接跳过。
function shouldFetchReport(status) {
  return status === 'FINISHED';
}

async function loadDetail(local) {
  const body = document.querySelector('[data-detail-body]');
  if (body) body.innerHTML = renderDetailSkeleton();
  try {
    local.detail = await requestJson(`${local.apiBase}/api/diagnose/tasks/${encodeURIComponent(local.taskNo)}/detail`);
    local.detailError = '';
  } catch (error) {
    local.detail = null;
    local.detailError = error.message || '事件详情加载失败';
  }
  if (body) {
    body.innerHTML = renderDetail(local);
    runReveal(body);
  }
  if (local.detail?.task && !shouldFetchReport(local.detail.task.status)) {
    // 进行中任务无报告，确保报告区域展示「暂未生成」而非报错。
    local.report = null;
    local.reportError = '';
    const downloadBtn = document.querySelector('[data-download]');
    if (downloadBtn) downloadBtn.hidden = true;
  }
  return local;
}

async function loadReport(local) {
  // 进行中/待启动任务尚无报告，避免触发后端 400「诊断报告不存在」。
  if (local.detail?.task && !shouldFetchReport(local.detail.task.status)) {
    local.report = null;
    local.reportError = '';
    return;
  }
  // detail 尚未加载完成时，仍尝试拉取；若任务进行中后端会返回 400，按报告不可用处理。
  try {
    const report = await requestJson(`${local.apiBase}/api/ai/diagnose/${encodeURIComponent(local.taskNo)}/report`);
    local.report = report;
    local.insight = extractInsightSummary(report);
    local.reportError = '';
  } catch (error) {
    local.report = null;
    local.reportError = error.message || '报告不可用';
  }
  const body = document.querySelector('[data-detail-body]');
  if (body && local.detail) {
    body.innerHTML = renderDetail(local);
    runReveal(body);
  }
  const downloadBtn = document.querySelector('[data-download]');
  if (downloadBtn) downloadBtn.hidden = !local.report?.reportMarkdown;
}

function downloadMarkdown(taskNo, markdown) {
  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `诊断报告-${taskNo || '未命名任务'}.md`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
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

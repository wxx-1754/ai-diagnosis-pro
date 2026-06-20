import anime from 'animejs/lib/anime.es.js';
import '@phosphor-icons/web/regular';
import { createEmptyInsightSummary, extractInsightSummary } from './insight-summary.js';
import { mountOverview } from './overview.js';
import { mountEventDetail } from './event-detail.js';
import { railHtml } from './rail.js';
import './styles.css';

const STREAM_EVENT_TYPES = [
  'TASK_CREATED',
  'INTENT_CLASSIFYING',
  'INTENT_CLASSIFIED',
  'PLAN_CREATED',
  'TOOL_CALL_START',
  'TOOL_CALL_SUCCESS',
  'TOOL_CALL_FAILED',
  'AI_ANALYZING',
  'REPORT_GENERATED',
  'TASK_FINISHED',
  'TASK_FAILED',
  'TASK_INTERRUPTED',
  'HEARTBEAT'
];

const EVENT_LABELS = {
  TASK_CREATED: '任务创建',
  INTENT_CLASSIFYING: '识别意图',
  INTENT_CLASSIFIED: '意图确认',
  PLAN_CREATED: '计划生成',
  TOOL_CALL_START: '工具调用',
  TOOL_CALL_SUCCESS: '采样成功',
  TOOL_CALL_FAILED: '采样失败',
  AI_ANALYZING: 'AI 分析',
  REPORT_GENERATED: '报告生成',
  TASK_FINISHED: '诊断完成',
  TASK_FAILED: '诊断失败',
  TASK_INTERRUPTED: '诊断中断',
  HEARTBEAT: '心跳',
  STREAM_ERROR: '连接异常',
  MESSAGE: '消息'
};

const EVENT_META = {
  TASK_CREATED: { icon: 'ph-plugs-connected', tone: 'blue' },
  INTENT_CLASSIFYING: { icon: 'ph-magnifying-glass', tone: 'cyan' },
  INTENT_CLASSIFIED: { icon: 'ph-crosshair', tone: 'cyan' },
  PLAN_CREATED: { icon: 'ph-list-checks', tone: 'blue' },
  TOOL_CALL_START: { icon: 'ph-terminal-window', tone: 'amber' },
  TOOL_CALL_SUCCESS: { icon: 'ph-check-circle', tone: 'green' },
  TOOL_CALL_FAILED: { icon: 'ph-warning', tone: 'red' },
  AI_ANALYZING: { icon: 'ph-brain', tone: 'violet' },
  REPORT_GENERATED: { icon: 'ph-file-text', tone: 'violet' },
  TASK_FINISHED: { icon: 'ph-seal-check', tone: 'green' },
  TASK_FAILED: { icon: 'ph-warning-octagon', tone: 'red' },
  TASK_INTERRUPTED: { icon: 'ph-plugs', tone: 'red' },
  STREAM_ERROR: { icon: 'ph-plugs', tone: 'red' },
  MESSAGE: { icon: 'ph-activity', tone: 'neutral' }
};

const ENVIRONMENT_LABELS = {
  dev: '开发环境',
  test: '测试环境',
  uat: '验收环境',
  staging: '预发布环境',
  prod: '生产环境'
};

const STAGES = [
  {
    id: 'entry',
    label: '慢请求入口',
    role: 'HTTP Evidence',
    metric: 'P95 3.21s',
    hint: '业务症状',
    icon: 'ph-gauge'
  },
  {
    id: 'intent',
    label: '意图识别',
    role: 'AI Classifier',
    metric: 'High CPU',
    hint: '问题归类',
    icon: 'ph-crosshair'
  },
  {
    id: 'plan',
    label: '诊断计划',
    role: 'Agent Planner',
    metric: '3 commands',
    hint: '采样路径',
    icon: 'ph-list-checks'
  },
  {
    id: 'arthas',
    label: 'Arthas 采样',
    role: 'Tool Calling',
    metric: 'thread -n 5',
    hint: '现场证据',
    icon: 'ph-terminal-window'
  },
  {
    id: 'reasoning',
    label: '根因推理',
    role: 'AI Analyzer',
    metric: 'RAG Ready',
    hint: '证据归因',
    icon: 'ph-brain'
  },
  {
    id: 'fix',
    label: '修复建议',
    role: 'Action Plan',
    metric: '待验证',
    hint: '回滚可控',
    icon: 'ph-wrench'
  }
];

const RUNBOOK = [
  {
    title: '收集与理解',
    detail: '创建诊断任务，订阅 SSE 事件流。',
    events: ['TASK_CREATED', 'INTENT_CLASSIFYING', 'INTENT_CLASSIFIED']
  },
  {
    title: '定位瓶颈',
    detail: '生成计划，锁定 Arthas 采样命令。',
    events: ['PLAN_CREATED']
  },
  {
    title: '现场采样',
    detail: '执行 thread、dashboard、jvm 等受控命令。',
    events: ['TOOL_CALL_START', 'TOOL_CALL_SUCCESS', 'TOOL_CALL_FAILED']
  },
  {
    title: '根因推理',
    detail: '融合命令结果与上下文，生成分析结论。',
    events: ['AI_ANALYZING', 'REPORT_GENERATED']
  },
  {
    title: '建议与验证',
    detail: '输出修复建议，等待人工验证。',
    events: ['TASK_FINISHED', 'TASK_FAILED']
  }
];

const EVENT_STAGE = {
  TASK_CREATED: 0,
  INTENT_CLASSIFYING: 1,
  INTENT_CLASSIFIED: 1,
  PLAN_CREATED: 2,
  TOOL_CALL_START: 3,
  TOOL_CALL_SUCCESS: 3,
  TOOL_CALL_FAILED: 3,
  AI_ANALYZING: 4,
  REPORT_GENERATED: 4,
  TASK_FINISHED: 5,
  TASK_FAILED: 5,
  TASK_INTERRUPTED: 5,
  STREAM_ERROR: 5
};

const PASSIVE_EVENT_TYPES = new Set(['HEARTBEAT']);
const CURRENT_TASK_STORAGE_KEY = 'ai-diagnosis.currentTaskNo';

const DEMO_EVENTS = [
  ['TASK_CREATED', 'Agent 任务已创建，开始接管现场。', { appId: 'order-service', env: 'prod' }],
  ['INTENT_CLASSIFYING', '正在识别异常类型与采样目标。'],
  ['INTENT_CLASSIFIED', '识别为高 CPU 与慢请求复合问题。', {
    diagnoseType: 'HIGH_CPU',
    confidence: 0.91,
    reason: '热点线程与慢请求同时出现'
  }],
  ['PLAN_CREATED', '已生成 Arthas 采样计划。', 'TOOL_CALLING'],
  ['TOOL_CALL_START', '执行 thread -n 5 获取热点线程。', { command: 'thread -n 5' }],
  ['TOOL_CALL_SUCCESS', '热点线程集中在 OrderService#createOrder。', {
    command: 'thread -n 5',
    costMillis: 428,
    outputExcerpt: 'OrderService#createOrder cpu=81.0%'
  }],
  ['AI_ANALYZING', '正在融合线程栈、目标 URI 和历史报告。'],
  ['REPORT_GENERATED', '已生成根因与修复建议。', {
    reportMarkdown: demoReport(),
    insightSummary: demoInsightSummary()
  }],
  ['TASK_FINISHED', '诊断完成，等待验证修复效果。', {
    reportMarkdown: demoReport(),
    insightSummary: demoInsightSummary()
  }]
];

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const state = {
  apiBase: normalizeApiBase(import.meta.env.VITE_API_BASE_URL || 'http://localhost:9001'),
  taskNo: '',
  appId: 'order-service',
  env: 'prod',
  instanceOptions: [],
  optionsLoading: true,
  optionsError: '',
  userId: 'admin',
  mode: 'TOOL_CALLING',
  question: '',
  targetClass: '',
  targetMethod: '',
  targetUri: '',
  symptomMetric: '',
  activeStage: 0,
  completedStages: new Set(),
  doneRunbook: new Set(),
  activeRunbook: 0,
  stageEvidence: createInitialStageEvidence({}),
  events: [],
  commandRecords: [],
  reportMarkdown: '',
  insightSummary: createEmptyInsightSummary(),
  resumeDiagnoseType: '',
  observationState: '',
  restartAllowed: false,
  lastEventId: 0,
  seenEventIds: new Set(),
  runbookTiming: createInitialRunbookTiming(),
  lastResponse: null,
  eventSource: null,
  demoTimer: null,
  loading: false,
  connection: 'READY'
};

function studioShell() {
  return `
  <div class="diagnosis-studio">
    ${railHtml('studio', { bottom: `
      <div class="rail-status">
        <span class="rail-state" data-connection-dot aria-hidden="true"></span>
        <span class="rail-status-text">实时事件流</span>
      </div>
      <button class="rail-stop" type="button" data-stop-stream aria-label="停止流" title="停止流">
        <i class="ph ph-stop" aria-hidden="true"></i>
        <span>停止流</span>
      </button>
    ` })}

    <div class="workspace">
      <header class="topbar reveal">
        <div class="title-block">
          <span>AI + Arthas</span>
          <h1>因果诊断</h1>
          <p>Causal Map Studio · 从症状到根因的实时路径</p>
          <div class="resume-badge" data-resume-badge hidden></div>
        </div>

        <div class="top-fields">
          <label>
            <span>服务</span>
            <select id="appId" disabled aria-label="服务">
              <option>正在加载服务...</option>
            </select>
          </label>
          <label>
            <span>环境</span>
            <select id="env" disabled aria-label="环境">
              <option>正在加载环境...</option>
            </select>
          </label>
          <div class="active-task-field" data-active-task hidden aria-live="polite">
            <span class="field-label">
              <span class="field-icon" aria-hidden="true">◎</span>
              活动事件
            </span>
            <strong data-active-task-value></strong>
          </div>
          <div class="session-field">
            <span>会话进度</span>
            <div class="session-strip" data-session-strip aria-live="polite">
              <strong data-progress-label>0 / ${STAGES.length} 步骤完成</strong>
              <div class="progress-track" aria-hidden="true">
                <span data-progress-bar></span>
              </div>
            </div>
          </div>
        </div>
      </header>

      <main class="studio-grid">
        <section id="map" class="map-panel reveal" aria-labelledby="map-title">
          <div class="map-toolbar">
            <div>
              <p class="panel-label">诊断现场</p>
              <h2 id="map-title">从症状到根因的实时路径</h2>
            </div>
            <div class="toolbar-actions">
              <button class="primary-button" type="button" data-start-agent>启动诊断</button>
              <button class="primary-button" type="button" data-restart-diagnosis hidden>重新诊断</button>
              <button class="secondary-button" type="button" data-quick-ai>AI 快速分析</button>
              <button class="ghost-button" type="button" data-demo-flow>演示流</button>
            </div>
          </div>

          <div class="incident-card">
            <div class="incident-copy">
              <span>异常描述</span>
              <textarea id="question" rows="3" placeholder="描述线上异常现象，例如：CPU 持续升高 / 接口响应变慢 / 线程偶发卡住……">${escapeHtml(state.question)}</textarea>
            </div>
            <details class="target-details">
              <summary>目标范围</summary>
              <div class="target-grid">
                <label>
                  <span>URI</span>
                  <input id="targetUri" value="${escapeHtml(state.targetUri)}" autocomplete="off" placeholder="可选，如 /api/orders" />
                </label>
                <label>
                  <span>Class</span>
                  <input id="targetClass" value="${escapeHtml(state.targetClass)}" autocomplete="off" placeholder="可选，全限定类名" />
                </label>
                <label>
                  <span>Method</span>
                  <input id="targetMethod" value="${escapeHtml(state.targetMethod)}" autocomplete="off" placeholder="可选，方法名" />
                </label>
              </div>
            </details>
          </div>

          <div class="graph-wrap" aria-label="因果图谱">
            <div class="graph-legend">
              <span><i class="legend-dot java"></i>Java 层</span>
              <span><i class="legend-dot arthas"></i>Arthas 采样</span>
              <span><i class="legend-dot ai"></i>AI 推理</span>
              <span><i class="legend-dot done"></i>已验证</span>
            </div>
            <div class="cause-map" data-cause-map>
              ${STAGES.map((stage, index) => `
                <article class="flow-node ${index === 0 ? 'is-active' : ''}" data-stage="${index}" data-stage-id="${stage.id}">
                  <div class="evidence-tag">
                    <span class="evidence-label">
                      <i class="ph ph-target" aria-hidden="true"></i>
                      Evidence
                    </span>
                    <strong data-stage-metric="${index}">${escapeHtml(stage.metric)}</strong>
                    <small data-stage-detail="${index}">${escapeHtml(stage.hint)}</small>
                  </div>
                  <div class="node-shell">
                    <i class="ph ${escapeHtml(stage.icon)} node-icon" aria-hidden="true"></i>
                    <small>${escapeHtml(stage.role)}</small>
                    <h3>${escapeHtml(stage.label)}</h3>
                    <p>${escapeHtml(stage.hint)}</p>
                  </div>
                  ${index < STAGES.length - 1 ? `<span class="flow-edge" data-edge="${index}"></span>` : ''}
                </article>
              `).join('')}
            </div>
          </div>

          <section id="plan" class="agent-plan" aria-label="智能体执行计划">
            <div class="plan-head">
              <div>
                <p class="panel-label">智能体执行计划</p>
                <h2>Agent runbook</h2>
              </div>
              <div class="plan-meta">
                <span data-mode-label>${escapeHtml(state.mode)}</span>
                <span data-cost-label>等待启动</span>
              </div>
            </div>
            <div class="plan-track" data-plan-track>
              ${RUNBOOK.map((step, index) => `
                <div class="plan-segment">
                  <article class="plan-step ${index === 0 ? 'is-active' : ''}" data-runbook="${index}">
                    <div class="plan-step-head">
                      <span class="plan-index">${index + 1}</span>
                      <span class="plan-status" data-runbook-status="${index}"></span>
                    </div>
                    <strong>${escapeHtml(step.title)}</strong>
                    <p>${escapeHtml(step.detail)}</p>
                    <small class="plan-duration" data-runbook-duration="${index}">等待执行</small>
                  </article>
                  ${index < RUNBOOK.length - 1 ? `
                    <span class="plan-connector" aria-hidden="true">
                      <i class="ph ph-arrow-right"></i>
                    </span>
                  ` : ''}
                </div>
              `).join('')}
            </div>
          </section>
        </section>

        <aside id="result" class="insight-panel reveal" aria-label="根因与建议">
          <section class="insight-card diagnosis-result-card">
            <div class="insight-heading">
              <p class="panel-label">根因与建议</p>
              <span data-status-pill>Ready</span>
            </div>
            <div class="result-section root-analysis">
              <div class="result-section-title">
                <i class="ph ph-brain" aria-hidden="true"></i>
                <h3>根因分析</h3>
              </div>
              <strong class="root-conclusion" data-root-title>等待诊断事件</strong>
              <p data-root-copy>启动 Agent 后，AI 将根据采样证据生成根因结论。</p>
            </div>
            <div class="result-section reason-section">
              <div class="result-section-title">
                <i class="ph ph-magnifying-glass" aria-hidden="true"></i>
                <h3>具体原因</h3>
              </div>
              <ul class="reason-list" data-reason-list>
                <li>等待 AI 从完整诊断报告中提炼具体原因。</li>
              </ul>
            </div>
            <div class="result-section effect-section">
              <div class="result-section-title">
                <i class="ph ph-trend-down" aria-hidden="true"></i>
                <h3>预期效果</h3>
              </div>
              <p data-expected-effect>暂无可靠估算，需在修复后验证。</p>
            </div>
            <div class="result-section action-section">
              <div class="result-section-title">
                <i class="ph ph-terminal-window" aria-hidden="true"></i>
                <h3>推荐操作</h3>
              </div>
              <ol class="action-list" data-action-list>
                <li>等待诊断完成后生成详细操作。</li>
              </ol>
            </div>
            <div class="report-download">
              <div>
                <strong>完整诊断报告</strong>
                <small>包含执行步骤、风险提示和结论摘要</small>
              </div>
              <button class="primary-button download-button" type="button" data-download-report disabled>
                <i class="ph ph-download-simple" aria-hidden="true"></i>
                下载 Markdown
              </button>
            </div>
          </section>
        </aside>
      </main>
    </div>
  </div>
`;
}

const refs = {
  appId: null,
  env: null,
  question: null,
  targetUri: null,
  targetClass: null,
  targetMethod: null,
  startAgent: null,
  restartDiagnosis: null,
  quickAi: null,
  demoFlow: null,
  stopStream: null,
  downloadReport: null
};

function populateRefs() {
  refs.appId = document.querySelector('#appId');
  refs.env = document.querySelector('#env');
  refs.question = document.querySelector('#question');
  refs.targetUri = document.querySelector('#targetUri');
  refs.targetClass = document.querySelector('#targetClass');
  refs.targetMethod = document.querySelector('#targetMethod');
  refs.startAgent = document.querySelector('[data-start-agent]');
  refs.restartDiagnosis = document.querySelector('[data-restart-diagnosis]');
  refs.quickAi = document.querySelector('[data-quick-ai]');
  refs.demoFlow = document.querySelector('[data-demo-flow]');
  refs.stopStream = document.querySelector('[data-stop-stream]');
  refs.downloadReport = document.querySelector('[data-download-report]');
}

function mountStudio(resumeTaskNo) {
  document.querySelector('#app').innerHTML = studioShell();
  populateRefs();
  bindEvents();
  renderAll();
  runIntroAnimation();
  // 接续观察与实例选项加载并行执行，互不阻塞；resumeTask 内部会在选项就绪后回填服务/环境。
  loadInstanceOptions();
  if (resumeTaskNo) {
    resumeTask(resumeTaskNo);
  }
}

// 接续观察：使用持久化诊断事件恢复真实现场；只有仍活跃的任务才连接 SSE。
async function resumeTask(taskNo) {
  try {
    resetRun();
    setTaskNo(taskNo);
    setConnection('RUNNING', '回放诊断现场');
    renderResumeBadge();

    const detail = await requestJson(`${state.apiBase}/api/diagnose/tasks/${encodeURIComponent(taskNo)}/detail`);
    const task = detail?.task || {};
    state.commandRecords = detail?.commandRecords || [];
    state.observationState = detail?.observationState || '';
    state.restartAllowed = Boolean(detail?.restartAllowed);
    state.lastEventId = Number(detail?.lastEventId) || 0;

    if (task.question) state.question = task.question;
    if (task.targetUri) state.targetUri = task.targetUri;
    if (task.targetClass) state.targetClass = task.targetClass;
    if (task.targetMethod) state.targetMethod = task.targetMethod;
    state.resumeDiagnoseType = task.diagnoseType || '';
    if (task.appId) state.appId = task.appId;
    if (task.env) state.env = task.env;

    if (refs.question) refs.question.value = state.question;
    if (refs.targetUri) refs.targetUri.value = state.targetUri;
    if (refs.targetClass) refs.targetClass.value = state.targetClass;
    if (refs.targetMethod) refs.targetMethod.value = state.targetMethod;

    if (state.instanceOptions.length) {
      renderServiceOptions();
    }

    replayPersistedEvents(detail?.events || []);
    renderMapState();
    renderResumeBadge();
    updateActionAvailability();

    if (state.observationState === 'ACTIVE') {
      rememberCurrentTask(taskNo);
      connectStream('', state.lastEventId);
      setConnection('RUNNING', '已接续现场，监听实时事件');
    } else if (state.observationState === 'INTERRUPTED') {
      forgetCurrentTask();
      setConnection('ERROR', '诊断已中断，后台执行已不存在');
    } else {
      forgetCurrentTask();
      if (task.status === 'FINISHED') {
        await refreshReport();
      }
      setConnection(task.status === 'FAILED' ? 'ERROR' : 'SUCCESS', task.status === 'FAILED' ? '诊断已失败' : '诊断已完成');
    }
  } catch (error) {
    console.error('[resumeTask] 回放诊断现场失败', error);
    setConnection('ERROR', error.message || '回放失败');
    showNotice(error.message || '回放诊断现场失败');
  }
}

function renderResumeBadge() {
  const badge = document.querySelector('[data-resume-badge]');
  if (!badge) return;
  if (!state.taskNo) {
    badge.hidden = true;
    badge.innerHTML = '';
    return;
  }
  const typeLabel = formatDiagnoseType(state.resumeDiagnoseType);
  const interrupted = state.observationState === 'INTERRUPTED';
  badge.hidden = false;
  badge.innerHTML = `
    <i class="ph ${interrupted ? 'ph-plugs' : 'ph-crosshair'}" aria-hidden="true"></i>
    <span>${interrupted ? '已中断' : '接续'} ${escapeHtml(state.taskNo)}</span>
    ${typeLabel ? `<span class="resume-badge-type">${escapeHtml(typeLabel)}</span>` : ''}
  `;
}

function replayPersistedEvents(events) {
  events.forEach((event) => pushEvent(event));
}

// 必须在 setupRouter() 之前声明，否则 handleRoute 访问时处于 TDZ。
let activeView = null;

setupRouter();

function bindEvents() {
  refs.appId.addEventListener('change', () => {
    state.appId = refs.appId.value;
    renderEnvironmentOptions();
    readInputs();
  });

  refs.env.addEventListener('change', readInputs);

  [refs.question, refs.targetUri, refs.targetClass, refs.targetMethod].forEach((input) => {
    input.addEventListener('change', readInputs);
  });

  refs.startAgent.addEventListener('click', startAgentDiagnosis);
  refs.restartDiagnosis.addEventListener('click', restartDiagnosis);
  refs.quickAi.addEventListener('click', runQuickAi);
  refs.demoFlow.addEventListener('click', playDemoFlow);
  refs.stopStream.addEventListener('click', () => closeLiveSources(true));
  refs.downloadReport.addEventListener('click', downloadReport);
}

async function loadInstanceOptions() {
  state.optionsLoading = true;
  state.optionsError = '';
  renderInstanceOptionState();
  updateActionAvailability();

  try {
    const options = await requestJson(`${state.apiBase}/api/app-instances/options`);
    state.instanceOptions = Array.isArray(options)
      ? options.filter((option) => option?.appId && option?.env)
      : [];

    if (!state.instanceOptions.length) {
      throw new Error('数据库中没有可用的在线服务实例。');
    }

    renderServiceOptions();
  } catch (error) {
    state.instanceOptions = [];
    state.optionsError = error.message || '服务与环境加载失败。';
    state.optionsLoading = false;
    renderInstanceOptionState();
    showNotice(state.optionsError);
  } finally {
    state.optionsLoading = false;
    updateActionAvailability();
  }
}

function renderInstanceOptionState() {
  if (state.optionsLoading) {
    refs.appId.innerHTML = '<option>正在加载服务...</option>';
    refs.env.innerHTML = '<option>正在加载环境...</option>';
  } else if (state.optionsError) {
    refs.appId.innerHTML = '<option>服务加载失败</option>';
    refs.env.innerHTML = '<option>环境加载失败</option>';
  }

  refs.appId.disabled = true;
  refs.env.disabled = true;
}

function renderServiceOptions() {
  const services = new Map();

  state.instanceOptions.forEach((option) => {
    if (!services.has(option.appId)) {
      services.set(option.appId, option.appName || option.appId);
    }
  });

  const appIds = [...services.keys()];
  state.appId = appIds.includes(state.appId) ? state.appId : appIds[0];
  refs.appId.innerHTML = appIds.map((appId) => {
    const appName = services.get(appId);
    const label = appName && appName !== appId ? `${appName} (${appId})` : appId;
    return `<option value="${escapeHtml(appId)}">${escapeHtml(label)}</option>`;
  }).join('');
  refs.appId.value = state.appId;
  refs.appId.disabled = false;
  renderEnvironmentOptions();
}

function renderEnvironmentOptions() {
  const environments = [...new Set(
    state.instanceOptions
      .filter((option) => option.appId === refs.appId.value)
      .map((option) => option.env)
  )];

  state.env = environments.includes(state.env) ? state.env : environments[0] || '';
  refs.env.innerHTML = environments.length
    ? environments.map((env, index) => `
        <option value="${escapeHtml(env)}">${escapeHtml(formatEnvironmentLabel(env, index))}</option>
      `).join('')
    : '<option>暂无可用环境</option>';
  refs.env.value = state.env;
  refs.env.disabled = environments.length === 0;
  updateActionAvailability();
}

function readInputs() {
  state.appId = refs.appId.value;
  state.env = refs.env.value;
  state.question = refs.question.value.trim();
  state.targetUri = refs.targetUri.value.trim();
  state.targetClass = refs.targetClass.value.trim();
  state.targetMethod = refs.targetMethod.value.trim();
}

async function startAgentDiagnosis() {
  readInputs();
  resetRun();

  await runAction('启动诊断', async () => {
    const response = await requestJson(`${state.apiBase}/api/agent/diagnose/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        appId: state.appId,
        env: state.env,
        userId: state.userId,
        question: state.question,
        targetUri: state.targetUri,
        targetClass: state.targetClass,
        targetMethod: state.targetMethod,
        mode: state.mode
      })
    });

    setTaskNo(response.taskNo, { persistInRoute: true, remember: true });
    connectStream(response.streamUrl, 0);
    renderResponse(response);
    return response;
  });
}

async function runQuickAi() {
  readInputs();
  resetRun();

  await runAction('AI 快速分析', async () => {
    const response = await requestJson(`${state.apiBase}/api/ai/diagnose`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        appId: state.appId,
        env: state.env,
        userId: state.userId,
        question: state.question,
        targetUri: state.targetUri,
        targetClass: state.targetClass,
        targetMethod: state.targetMethod
      })
    });

    setTaskNo(response.taskNo);
    state.reportMarkdown = response.reportMarkdown || '';
    state.insightSummary = extractInsightSummary(response);
    pushEvent({ eventType: 'TASK_CREATED', message: 'AI 快速诊断任务已创建。', success: true });
    pushEvent({ eventType: 'REPORT_GENERATED', message: 'AI 诊断报告已返回。', success: true, data: response });
    pushEvent({ eventType: 'TASK_FINISHED', message: '快速分析已完成。', success: true, data: response });
    renderResponse(response);
    renderReport();
    return response;
  });
}

function playDemoFlow() {
  closeLiveSources(false);
  resetRun();
  setTaskNo(`DEMO-${new Date().toISOString().slice(11, 19).replaceAll(':', '')}`);
  setConnection('RUNNING', '演示流运行中');

  let index = 0;
  state.demoTimer = window.setInterval(() => {
    const current = DEMO_EVENTS[index];
    if (!current) {
      closeLiveSources(false);
      setConnection('SUCCESS', '演示完成');
      return;
    }

    pushEvent({
      taskNo: state.taskNo,
      eventType: current[0],
      message: current[1],
      command: current[2]?.command || '',
      toolName: current[0].startsWith('TOOL') ? 'Arthas' : '',
      success: !current[0].includes('FAILED'),
      time: new Date().toISOString(),
      data: current[2] || null
    });
    index += 1;
  }, 720);
}

function connectStream(streamUrl, afterEventId = state.lastEventId) {
  if (!state.taskNo) return;

  const url = appendAfterEventId(resolveStreamUrl(streamUrl), afterEventId);
  const source = new EventSource(url);
  state.eventSource = source;
  setConnection('RUNNING', '正在连接 SSE');

  source.onopen = () => {
    if (state.eventSource === source) {
      setConnection('RUNNING', 'SSE 已连接');
    }
  };

  STREAM_EVENT_TYPES.forEach((eventType) => {
    source.addEventListener(eventType, (event) => handleSseEvent(eventType, event));
  });

  source.onmessage = (event) => handleSseEvent('MESSAGE', event);
  source.onerror = () => {
    if (state.eventSource !== source) return;
    setConnection('RUNNING', 'SSE 连接中断，正在重连');
  };
}

function handleSseEvent(eventType, event) {
  const payload = safeJsonParse(event.data || '{}');
  const eventPayload = {
    ...payload,
    id: Number(payload.id || event.lastEventId) || undefined,
    eventType: payload.eventType || eventType,
    time: payload.time || new Date().toISOString()
  };

  if (PASSIVE_EVENT_TYPES.has(eventPayload.eventType)) {
    setConnection('RUNNING', 'SSE 已连接');
    return;
  }

  pushEvent(eventPayload);

  if (eventPayload.eventType === 'REPORT_GENERATED') {
    state.reportMarkdown = extractReportMarkdown(eventPayload.data) || state.reportMarkdown;
    state.insightSummary = extractInsightSummary(eventPayload.data);
    renderReport();
  }

  if (eventPayload.eventType === 'TASK_FINISHED') {
    forgetCurrentTask();
    closeLiveSources(false);
    setConnection('SUCCESS', '诊断完成');
    refreshTaskDetail({ silent: true });
  }

  if (eventPayload.eventType === 'TASK_FAILED') {
    forgetCurrentTask();
    closeLiveSources(false);
    setConnection('ERROR', '诊断失败');
    refreshTaskDetail({ silent: true });
  }

  if (eventPayload.eventType === 'TASK_INTERRUPTED') {
    forgetCurrentTask();
    state.observationState = 'INTERRUPTED';
    state.restartAllowed = true;
    closeLiveSources(false);
    updateActionAvailability();
    setConnection('ERROR', '诊断已中断，后台执行已不存在');
  }
}

function pushEvent(event) {
  const eventId = Number(event.id) || 0;
  if (eventId && state.seenEventIds.has(eventId)) return;
  if (eventId) {
    state.seenEventIds.add(eventId);
    state.lastEventId = Math.max(state.lastEventId, eventId);
  }
  const normalized = {
    id: eventId || undefined,
    taskNo: event.taskNo || state.taskNo,
    eventType: event.eventType || 'MESSAGE',
    message: event.message || '',
    command: event.command || '',
    toolName: event.toolName || '',
    success: event.success,
    data: event.data,
    time: event.time || new Date().toISOString()
  };

  const stage = EVENT_STAGE[normalized.eventType];
  const previousStage = state.activeStage;
  let stageChanged = false;

  if (Number.isInteger(stage)) {
    state.activeStage = Math.max(state.activeStage, stage);
    stageChanged = state.activeStage !== previousStage;
    state.stageEvidence[stage] = deriveStageEvidence(normalized);

    for (let index = 0; index < state.activeStage; index += 1) {
      state.completedStages.add(index);
    }

    if (normalized.eventType === 'TASK_FINISHED') {
      STAGES.forEach((_, index) => state.completedStages.add(index));
      state.activeStage = STAGES.length - 1;
    }
  }

  const runbookIndex = RUNBOOK.findIndex((item) => item.events.includes(normalized.eventType));
  const previousRunbook = state.activeRunbook;

  if (normalized.eventType === 'TASK_FAILED' || normalized.eventType === 'STREAM_ERROR') {
    failRunbookStep(state.activeRunbook, normalized.time);
  } else if (runbookIndex >= 0) {
    updateRunbookTiming(runbookIndex, normalized);
    state.activeRunbook = Math.max(state.activeRunbook, runbookIndex);

    for (let doneIndex = 0; doneIndex < state.activeRunbook; doneIndex += 1) {
      state.doneRunbook.add(doneIndex);
    }

    if (normalized.eventType === 'TASK_FINISHED') {
      RUNBOOK.forEach((_, index) => state.doneRunbook.add(index));
      state.activeRunbook = RUNBOOK.length - 1;
    }
  }

  if (normalized.eventType === 'REPORT_GENERATED') {
    state.reportMarkdown = extractReportMarkdown(normalized.data) || state.reportMarkdown;
    state.insightSummary = extractInsightSummary(normalized.data);
    renderReport();
  }

  state.events = [normalized, ...state.events].slice(0, 18);
  renderEventDrivenState();
  animateEvent(Number.isInteger(stage) && stageChanged ? state.activeStage : null, previousRunbook !== state.activeRunbook);
}

async function refreshTaskDetail(options = {}) {
  if (!state.taskNo) {
    showNotice('先启动诊断或填写任务号。');
    return null;
  }

  const action = async () => {
    const detail = await requestJson(`${state.apiBase}/api/diagnose/tasks/${encodeURIComponent(state.taskNo)}/detail`);
    state.commandRecords = detail.commandRecords || [];
    renderResponse(detail);
    return detail;
  };

  if (options.silent) {
    try {
      return await action();
    } catch {
      return null;
    }
  }

  return runAction('刷新详情', action);
}

async function refreshReport() {
  if (!state.taskNo) {
    showNotice('先启动诊断或填写任务号。');
    return null;
  }

  return runAction('刷新报告', async () => {
    const report = await requestJson(`${state.apiBase}/api/ai/diagnose/${encodeURIComponent(state.taskNo)}/report`);
    state.reportMarkdown = report.reportMarkdown || '';
    state.insightSummary = extractInsightSummary(report);
    renderReport();
    renderResponse(report);
    return report;
  });
}

async function runAction(label, action) {
  if (state.loading) return null;

  setLoading(true, label);
  try {
    const response = await action();
    setConnection(state.eventSource || state.demoTimer ? 'RUNNING' : 'SUCCESS', `${label}成功`);
    return response;
  } catch (error) {
    const payload = {
      code: error.code || 'REQUEST_ERROR',
      message: error.message || String(error)
    };
    renderResponse(payload);
    showNotice(payload.message);
    setConnection('ERROR', `${label}失败`);
    return null;
  } finally {
    setLoading(false);
  }
}

async function requestJson(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  const payload = text ? safeJsonParse(text) : {};

  if (!response.ok) {
    const error = new Error(payload.message || `HTTP ${response.status}`);
    error.code = payload.code;
    throw error;
  }

  return payload;
}

function renderAll() {
  renderEventDrivenState();
  renderReport();
  renderResponse(null);
}

function renderEventDrivenState() {
  renderMapState();
  renderRunbook();
  renderRootCard();
  renderProgress();
}

function renderMapState() {
  document.querySelectorAll('[data-stage]').forEach((node) => {
    const index = Number(node.dataset.stage);
    const evidence = state.stageEvidence[index] || createInitialStageEvidence({
      symptomMetric: state.symptomMetric,
      targetUri: state.targetUri
    })[index];
    const meta = getEventMeta(evidence.eventType);
    node.classList.toggle('is-active', index === state.activeStage);
    node.classList.toggle('is-done', state.completedStages.has(index));
    node.classList.toggle('is-error', state.events[0]?.eventType === 'TASK_FAILED' && index === state.activeStage);
    node.dataset.tone = meta.tone;
    node.querySelector(`[data-stage-metric="${index}"]`).textContent = evidence.primary;
    node.querySelector(`[data-stage-detail="${index}"]`).textContent = evidence.detail;
  });

  document.querySelectorAll('[data-edge]').forEach((edge) => {
    const index = Number(edge.dataset.edge);
    edge.classList.toggle('is-hot', index < state.activeStage);
  });
}

function renderRunbook() {
  document.querySelectorAll('[data-runbook]').forEach((step) => {
    const index = Number(step.dataset.runbook);
    const timing = state.runbookTiming[index];
    const status = timing?.status || 'pending';
    step.classList.toggle('is-active', status === 'running');
    step.classList.toggle('is-done', status === 'done');
    step.classList.toggle('is-failed', status === 'failed');

    const statusNode = step.querySelector(`[data-runbook-status="${index}"]`);
    statusNode.innerHTML = status === 'done'
      ? '<i class="ph ph-check-circle" aria-hidden="true"></i>已完成'
      : status === 'failed'
        ? '<i class="ph ph-warning-circle" aria-hidden="true"></i>失败'
        : status === 'running'
          ? '<i class="ph ph-spinner-gap" aria-hidden="true"></i>执行中'
          : '';

    step.querySelector(`[data-runbook-duration="${index}"]`).textContent = formatRunbookDuration(timing);
  });
}

function renderRootCard() {
  const latest = state.events[0];
  const insight = state.insightSummary;
  const statusText = state.connection === 'RUNNING'
    ? '分析中'
    : state.connection === 'SUCCESS'
      ? '已完成'
      : state.connection === 'ERROR'
        ? '需检查'
        : 'Ready';

  document.querySelector('[data-status-pill]').textContent = statusText;
  document.querySelector('[data-reason-list]').innerHTML = (insight.specificReasons.length
    ? insight.specificReasons
    : ['等待 AI 从完整诊断报告中提炼具体原因。'])
    .map((reason) => `<li>${escapeHtml(reason)}</li>`)
    .join('');
  document.querySelector('[data-expected-effect]').textContent = insight.expectedEffect
    || '等待 AI 根据完整诊断报告生成简要效果摘要。';
  document.querySelector('[data-action-list]').innerHTML = (insight.recommendedActions.length
    ? insight.recommendedActions
    : ['等待 AI 根据完整诊断报告生成简要操作。'])
    .map((action) => `<li>${escapeHtml(action)}</li>`)
    .join('');
  refs.downloadReport.disabled = !state.reportMarkdown;

  if (insight.rootCause) {
    document.querySelector('[data-root-title]').textContent = insight.rootCause;
    document.querySelector('[data-root-copy]').textContent = '由 AI 基于完整诊断报告二次提炼。';
    return;
  }

  if (!latest) {
    document.querySelector('[data-root-title]').textContent = '等待诊断事件';
    document.querySelector('[data-root-copy]').textContent = '启动 Agent 后，SSE 事件会推动图谱节点、计划步骤和报告区域同步更新。';
    return;
  }

  if (latest.eventType === 'TOOL_CALL_SUCCESS') {
    document.querySelector('[data-root-title]').textContent = 'Arthas 已采集到证据';
    document.querySelector('[data-root-copy]').textContent = latest.message || '工具调用成功，正在进入 AI 根因推理。';
    return;
  }

  if (latest.eventType === 'REPORT_GENERATED') {
    document.querySelector('[data-root-title]').textContent = '根因分析已生成';
    document.querySelector('[data-root-copy]').textContent = '右侧报告已更新，可继续刷新任务详情查看命令证据链。';
    return;
  }

  if (latest.eventType === 'TASK_FAILED' || latest.eventType === 'STREAM_ERROR') {
    document.querySelector('[data-root-title]').textContent = '链路需要检查';
    document.querySelector('[data-root-copy]').textContent = latest.message || '后端连接或诊断任务失败，请检查服务地址和 AI 开关。';
    return;
  }

  document.querySelector('[data-root-title]').textContent = EVENT_LABELS[latest.eventType] || latest.eventType;
  document.querySelector('[data-root-copy]').textContent = latest.message || '诊断流程正在推进。';
}

function renderReport() {
  renderRootCard();
}

function renderProgress() {
  const doneCount = Math.max(state.completedStages.size, state.activeStage);
  const isComplete = state.completedStages.size >= STAGES.length;
  const visibleCount = isComplete
    ? STAGES.length
    : Math.min(doneCount + (state.events.length ? 1 : 0), STAGES.length);
  const progress = Math.min(100, Math.round((visibleCount / STAGES.length) * 100));
  const progressLabel = document.querySelector('[data-progress-label]');
  const sessionStrip = document.querySelector('[data-session-strip]');
  progressLabel.classList.toggle('is-complete', isComplete);
  sessionStrip.classList.toggle('is-complete', isComplete);
  progressLabel.innerHTML = isComplete
    ? '<span class="status-icon" aria-hidden="true">✓</span>已完成分析'
    : `${visibleCount} / ${STAGES.length} 步骤完成`;
  document.querySelector('[data-progress-bar]').style.width = `${progress}%`;
  renderActiveTask();
  document.querySelector('[data-cost-label]').textContent = state.events[0] ? EVENT_LABELS[state.events[0].eventType] || state.events[0].eventType : '等待启动';
}

function renderActiveTask() {
  const field = document.querySelector('[data-active-task]');
  const topFields = document.querySelector('.top-fields');
  field.hidden = !state.taskNo;
  topFields.classList.toggle('has-active-task', Boolean(state.taskNo));
  document.querySelector('[data-active-task-value]').textContent = state.taskNo;
}

function renderResponse(response) {
  state.lastResponse = response;
}

function setTaskNo(taskNo, { persistInRoute = false, remember = false } = {}) {
  state.taskNo = taskNo || '';
  if (remember && state.taskNo) {
    rememberCurrentTask(state.taskNo);
  }
  if (persistInRoute && state.taskNo) {
    const route = `#/studio?taskNo=${encodeURIComponent(state.taskNo)}`;
    window.history.replaceState(window.history.state, '', route);
  }
  renderProgress();
}

function rememberCurrentTask(taskNo) {
  if (taskNo) {
    window.sessionStorage.setItem(CURRENT_TASK_STORAGE_KEY, taskNo);
  }
}

function forgetCurrentTask() {
  window.sessionStorage.removeItem(CURRENT_TASK_STORAGE_KEY);
  const raw = location.hash.replace(/^#/, '');
  if (raw.startsWith('/studio?') || raw.startsWith('studio?')) {
    window.history.replaceState(window.history.state, '', '#/studio');
  }
}

function setLoading(loading, label = '') {
  state.loading = loading;
  updateActionAvailability();
  if (loading) setConnection('RUNNING', `${label}中`);
}

function updateActionAvailability() {
  const targetUnavailable = state.optionsLoading || state.instanceOptions.length === 0 || !state.appId || !state.env;
  refs.startAgent.hidden = state.restartAllowed;
  refs.restartDiagnosis.hidden = !state.restartAllowed;
  refs.restartDiagnosis.disabled = state.loading || !state.restartAllowed;
  refs.startAgent.disabled = state.loading || targetUnavailable || state.restartAllowed;
  refs.quickAi.disabled = state.loading || targetUnavailable || state.restartAllowed;
  refs.appId.disabled = state.restartAllowed || state.optionsLoading || state.instanceOptions.length === 0;
  refs.env.disabled = state.restartAllowed || state.optionsLoading || !state.env;
  [refs.question, refs.targetUri, refs.targetClass, refs.targetMethod].forEach((input) => {
    input.disabled = state.restartAllowed;
  });
  refs.demoFlow.disabled = state.loading;
  refs.downloadReport.disabled = state.loading || !state.reportMarkdown;
}

function setConnection(connection, text) {
  state.connection = connection;
  document.querySelector('[data-connection-dot]').dataset.state = connection.toLowerCase();
  document.querySelector('[data-status-pill]').textContent = text || connection;
}

function resetRun() {
  closeLiveSources(false);
  resetDiagnosisState();
  renderAll();
  renderResumeBadge();
}

function resetDiagnosisState({ clearInputs = false } = {}) {
  if (clearInputs) {
    state.question = '';
    state.targetUri = '';
    state.targetClass = '';
    state.targetMethod = '';
  }
  state.taskNo = '';
  state.events = [];
  state.commandRecords = [];
  state.reportMarkdown = '';
  state.insightSummary = createEmptyInsightSummary();
  state.resumeDiagnoseType = '';
  state.observationState = '';
  state.restartAllowed = false;
  state.lastEventId = 0;
  state.seenEventIds = new Set();
  state.runbookTiming = createInitialRunbookTiming();
  state.completedStages = new Set();
  state.doneRunbook = new Set();
  state.activeStage = 0;
  state.activeRunbook = 0;
  state.stageEvidence = createInitialStageEvidence({
    symptomMetric: state.symptomMetric,
    targetUri: state.targetUri
  });
}

function closeLiveSources(recordEvent) {
  if (state.eventSource) {
    state.eventSource.close();
    state.eventSource = null;
  }

  if (state.demoTimer) {
    window.clearInterval(state.demoTimer);
    state.demoTimer = null;
  }

  if (recordEvent) {
    pushEvent({
      eventType: 'MESSAGE',
      message: '已停止当前事件流。',
      success: true,
      time: new Date().toISOString()
    });
  }
}

function showNotice(message) {
  setConnection('ERROR', message);
}

function resolveStreamUrl(streamUrl) {
  if (streamUrl?.startsWith('http')) return streamUrl;
  if (streamUrl?.startsWith('/')) return `${state.apiBase}${streamUrl}`;
  return `${state.apiBase}/api/diagnose/tasks/${encodeURIComponent(state.taskNo)}/stream`;
}

function appendAfterEventId(url, afterEventId) {
  const separator = url.includes('?') ? '&' : '?';
  return `${url}${separator}afterEventId=${encodeURIComponent(Math.max(0, Number(afterEventId) || 0))}`;
}

async function restartDiagnosis() {
  if (!state.taskNo || !state.restartAllowed) return;
  await runAction('重新诊断', async () => {
    const response = await requestJson(
      `${state.apiBase}/api/agent/diagnose/${encodeURIComponent(state.taskNo)}/restart`,
      { method: 'POST' }
    );
    location.hash = `#/studio?taskNo=${encodeURIComponent(response.taskNo)}`;
    return response;
  });
}

function normalizeApiBase(value) {
  return String(value || 'http://localhost:9001').trim().replace(/\/+$/, '');
}

function formatEnvironmentLabel(env, index) {
  const normalized = String(env || '').trim().toLowerCase();
  return ENVIRONMENT_LABELS[normalized] || `其他环境 ${index + 1}`;
}

function safeJsonParse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

function extractReportMarkdown(data) {
  if (!data) return '';
  if (typeof data === 'string') return data;
  return data.reportMarkdown || data.markdown || data.data?.reportMarkdown || '';
}

function downloadReport() {
  if (!state.reportMarkdown) return;
  const blob = new Blob([state.reportMarkdown], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `诊断报告-${state.taskNo || '未命名任务'}.md`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function formatTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value || '').slice(0, 19);
  return date.toLocaleTimeString('zh-CN', { hour12: false });
}

function createInitialRunbookTiming() {
  return RUNBOOK.map(() => ({
    status: 'pending',
    startedAt: null,
    finishedAt: null
  }));
}

function updateRunbookTiming(index, event) {
  const eventTime = parseEventTime(event.time);
  const timing = state.runbookTiming[index];

  if (!timing.startedAt) timing.startedAt = eventTime;
  timing.status = 'running';

  for (let previousIndex = 0; previousIndex < index; previousIndex += 1) {
    const previous = state.runbookTiming[previousIndex];
    if (!previous.startedAt) previous.startedAt = eventTime;
    if (!previous.finishedAt) previous.finishedAt = eventTime;
    if (previous.status !== 'failed') previous.status = 'done';
  }

  if (event.eventType === 'TASK_FINISHED') {
    state.runbookTiming.forEach((item) => {
      if (!item.startedAt) item.startedAt = eventTime;
      if (!item.finishedAt) item.finishedAt = eventTime;
      if (item.status !== 'failed') item.status = 'done';
    });
  }
}

function failRunbookStep(index, time) {
  const timing = state.runbookTiming[index];
  const eventTime = parseEventTime(time);
  if (!timing.startedAt) timing.startedAt = eventTime;
  timing.finishedAt = eventTime;
  timing.status = 'failed';
}

function parseEventTime(value) {
  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) ? timestamp : Date.now();
}

function formatRunbookDuration(timing) {
  if (!timing?.startedAt) return '等待执行';
  const end = timing.finishedAt || Date.now();
  const duration = Math.max(0, end - timing.startedAt);
  if (duration < 1000) return timing.status === 'running' ? '执行中 · <1s' : '耗时 <1s';
  const seconds = Math.round(duration / 1000);
  return timing.status === 'running' ? `执行中 · ${seconds}s` : `耗时 ${seconds}s`;
}

function createInitialStageEvidence(seed = {}) {
  return [
    {
      primary: seed.symptomMetric || '待填写',
      detail: seed.targetUri || '描述异常现象后开始诊断',
      eventType: 'TASK_CREATED'
    },
    { primary: '等待识别', detail: 'AI 分类器', eventType: 'INTENT_CLASSIFYING' },
    { primary: '等待计划', detail: '尚未生成采样路径', eventType: 'PLAN_CREATED' },
    { primary: '等待采样', detail: '尚无 Arthas 命令', eventType: 'TOOL_CALL_START' },
    { primary: '等待推理', detail: '尚无证据摘要', eventType: 'AI_ANALYZING' },
    { primary: '等待建议', detail: '诊断闭环尚未完成', eventType: 'TASK_FINISHED' }
  ];
}

function deriveStageEvidence(event) {
  const data = event.data;
  const dataObject = data && typeof data === 'object' ? data : {};
  const command = event.command || dataObject.command || '';
  const message = compactEvidence(event.message, 42);

  if (event.eventType === 'TASK_CREATED') {
    const target = [dataObject.appId || state.appId, dataObject.env || state.env].filter(Boolean).join(' / ');
    return {
      primary: compactEvidence(event.taskNo || state.symptomMetric, 28),
      detail: compactEvidence(target || state.targetUri || message, 38),
      eventType: event.eventType
    };
  }

  if (event.eventType === 'INTENT_CLASSIFYING') {
    return {
      primary: '正在识别',
      detail: compactEvidence(state.targetClass || state.question || message, 38),
      eventType: event.eventType
    };
  }

  if (event.eventType === 'INTENT_CLASSIFIED') {
    const confidence = Number(dataObject.confidence);
    const confidenceLabel = Number.isFinite(confidence) ? `${Math.round(confidence * 100)}% 置信度` : '';
    return {
      primary: compactEvidence(formatDiagnoseType(dataObject.diagnoseType) || message, 28),
      detail: compactEvidence(confidenceLabel || dataObject.reason || message, 38),
      eventType: event.eventType
    };
  }

  if (event.eventType === 'PLAN_CREATED') {
    const mode = typeof data === 'string' ? data : dataObject.mode;
    return {
      primary: compactEvidence(mode || state.mode || '诊断计划已生成', 28),
      detail: compactEvidence(message || '准备执行受控采样', 38),
      eventType: event.eventType
    };
  }

  if (event.eventType.startsWith('TOOL_CALL')) {
    const cost = Number(dataObject.costMillis);
    const detail = event.success === false
      ? dataObject.errorMessage || message
      : Number.isFinite(cost)
        ? `耗时 ${cost}ms${dataObject.requestNo ? ` / ${dataObject.requestNo}` : ''}`
        : dataObject.outputExcerpt || message;
    return {
      primary: compactEvidence(command || event.toolName || 'Arthas 命令', 30),
      detail: compactEvidence(detail, 42),
      eventType: event.eventType
    };
  }

  if (event.eventType === 'AI_ANALYZING') {
    const samples = state.events.filter((item) => item.eventType === 'TOOL_CALL_SUCCESS').length;
    return {
      primary: samples ? `融合 ${samples} 条采样证据` : '证据融合中',
      detail: compactEvidence(message, 38),
      eventType: event.eventType
    };
  }

  if (event.eventType === 'REPORT_GENERATED') {
    return {
      primary: '根因报告已生成',
      detail: compactEvidence(extractReportHeadline(data) || message, 42),
      eventType: event.eventType
    };
  }

  if (event.eventType === 'TASK_FINISHED') {
    return {
      primary: '诊断闭环完成',
      detail: compactEvidence(dataObject.conclusion || extractReportHeadline(data) || message, 42),
      eventType: event.eventType
    };
  }

  if (event.eventType === 'TASK_FAILED' || event.eventType === 'TASK_INTERRUPTED' || event.eventType === 'STREAM_ERROR') {
    return {
      primary: '诊断链路异常',
      detail: compactEvidence(message, 42),
      eventType: event.eventType
    };
  }

  return {
    primary: compactEvidence(EVENT_LABELS[event.eventType] || message, 28),
    detail: compactEvidence(message, 42),
    eventType: event.eventType
  };
}

function getEventMeta(eventType) {
  return EVENT_META[eventType] || EVENT_META.MESSAGE;
}

function formatDiagnoseType(value) {
  const labels = {
    HIGH_CPU: '高 CPU',
    MEMORY_ABNORMAL: '内存异常',
    THREAD_BLOCKED: '线程阻塞',
    SLOW_REQUEST: '接口慢',
    UNKNOWN: '待确认'
  };
  return labels[String(value || '').toUpperCase()] || String(value || '');
}

function extractReportHeadline(data) {
  const markdown = extractReportMarkdown(data) || (typeof data === 'string' ? data : '');
  const line = String(markdown)
    .split('\n')
    .map((item) => item.replace(/^#+\s*/, '').trim())
    .find(Boolean);
  return line || '';
}

function compactEvidence(value, maxLength = 38) {
  const text = String(value || '')
    .replace(/\s+/g, ' ')
    .replace(/^AI\s*/i, '')
    .trim();
  if (!text) return '等待事件数据';
  return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text;
}

function demoReport() {
  return [
    '# Java 应用智能诊断报告',
    '',
    '## 4. 关键发现',
    '- OrderService#createOrder 占用热点线程 81.0% 的 CPU 时间。',
    '- 订单创建链路的业务方法耗时显著高于其他采样方法。',
    '',
    '## 5. 根因分析',
    '订单创建方法中的高开销计算导致热点线程持续占用 CPU，并抬高接口 P95。',
    '',
    '## 6. 预期效果',
    '预计优化后 CPU 峰值可降低 25%–40%，P95 可降低 30%–45%；最终效果需通过同流量压测验证。',
    '',
    '## 7. 推荐操作',
    '1. 检查并优化 OrderService#createOrder 中的循环与重复计算，增加对应单元测试。',
    '2. 在灰度环境发布后，以相同流量观察 CPU、P95 和错误率至少 15 分钟。',
    '3. 若指标未回落，继续复查下游库存接口耗时与超时配置。'
  ].join('\n');
}

function demoInsightSummary() {
  return {
    rootCause: '订单创建方法的高开销计算持续占用热点线程。',
    specificReasons: [
      '热点线程的 CPU 时间主要集中在 createOrder。',
      '订单创建链路的业务方法耗时显著偏高。'
    ],
    expectedEffect: '预计 CPU 峰值和接口 P95 明显下降，最终需同流量压测验证。',
    recommendedActions: [
      '优化 createOrder 中的循环与重复计算。',
      '灰度发布后观察 CPU、P95 和错误率。',
      '指标未回落时复查下游库存接口。'
    ]
  };
}

function runIntroAnimation() {
  if (reduceMotion) {
    document.querySelectorAll('.reveal').forEach((item) => item.classList.add('is-visible'));
    return;
  }

  anime({
    targets: '.reveal',
    opacity: [0, 1],
    translateY: [12, 0],
    delay: anime.stagger(70),
    duration: 620,
    easing: 'easeOutCubic'
  });
}

function animateEvent(stage, runbookChanged = false) {
  if (reduceMotion) return;

  const node = Number.isInteger(stage) ? document.querySelector(`[data-stage="${stage}"] .node-shell`) : null;
  const nodeIcon = Number.isInteger(stage) ? document.querySelector(`[data-stage="${stage}"] .node-icon`) : null;
  const evidence = Number.isInteger(stage) ? document.querySelector(`[data-stage="${stage}"] .evidence-tag`) : null;
  const streamItem = document.querySelector('.stream-item');
  const planStep = runbookChanged ? document.querySelector(`[data-runbook="${state.activeRunbook}"]`) : null;
  const completedPlanStep = runbookChanged && state.activeRunbook > 0
    ? document.querySelector(`[data-runbook="${state.activeRunbook - 1}"]`)
    : null;
  const edge = Number.isInteger(stage) && stage > 0 ? document.querySelector(`[data-edge="${stage - 1}"]`) : null;

  [node, planStep].filter(Boolean).forEach((target) => {
    anime.remove(target);
    anime({
      targets: target,
      scale: [0.98, 1],
      translateY: [6, 0],
      duration: 420,
      easing: 'easeOutCubic'
    });
  });

  if (completedPlanStep) {
    anime.remove(completedPlanStep);
    anime({
      targets: completedPlanStep,
      scale: [1.02, 1],
      boxShadow: ['0 0 32px rgba(102, 216, 120, 0.3)', '0 0 0 rgba(102, 216, 120, 0)'],
      duration: 620,
      easing: 'easeOutCubic'
    });
  }

  if (streamItem) {
    anime.remove(streamItem);
    anime({
      targets: streamItem,
      opacity: [0, 1],
      translateX: [14, 0],
      duration: 460,
      easing: 'easeOutExpo'
    });
  }

  if (evidence) {
    anime.remove(evidence);
    anime({
      targets: evidence,
      opacity: [0.55, 1],
      translateY: [-4, 0],
      duration: 380,
      easing: 'easeOutCubic'
    });
  }

  if (nodeIcon) {
    anime.remove(nodeIcon);
    anime({
      targets: nodeIcon,
      scale: [0.72, 1],
      rotate: ['-8deg', '0deg'],
      opacity: [0.45, 1],
      duration: 520,
      easing: 'easeOutBack'
    });
  }

  if (edge) {
    anime.remove(edge);
    anime({
      targets: edge,
      scaleX: [0.15, 1],
      opacity: [0.2, 1],
      duration: 520,
      easing: 'easeOutCubic'
    });
  }
}

function animatePress(button) {
  if (reduceMotion) return;
  anime.remove(button);
  anime({
    targets: button,
    scale: [0.97, 1],
    duration: 220,
    easing: 'easeOutCubic'
  });
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function setupRouter() {
  window.addEventListener('hashchange', handleRoute);
  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-route]');
    if (!button) return;
    let route = button.getAttribute('data-route');
    if (route === '/studio') {
      const startsNewDiagnosis = button.hasAttribute('data-new-diagnosis');
      if (startsNewDiagnosis) {
        forgetCurrentTask();
        resetDiagnosisState({ clearInputs: true });
      }
      const currentTaskNo = startsNewDiagnosis
        ? ''
        : window.sessionStorage.getItem(CURRENT_TASK_STORAGE_KEY);
      if (currentTaskNo) {
        route = `/studio?taskNo=${encodeURIComponent(currentTaskNo)}`;
      } else if (!startsNewDiagnosis) {
        resetDiagnosisState({ clearInputs: true });
      }
    }
    if (route && location.hash !== `#${route}`) {
      location.hash = route;
    }
  });
  handleRoute();
}

function handleRoute() {
  const raw = location.hash.replace(/^#/, '');
  const [path, query = ''] = raw.split('?');
  const segments = path.split('/').filter(Boolean);
  const root = segments[0] || 'studio';

  // 离开诊断现场时释放 SSE 与演示流；停留在现场则保持不动，避免中断进行中的诊断。
  if (root !== 'studio' && activeView === 'studio') {
    closeLiveSources(false);
  }

  const app = document.querySelector('#app');
  if (root === 'overview') {
    const taskNo = segments[1];
    if (taskNo) {
      mountEventDetail(app, {
        taskNo: decodeURIComponent(taskNo),
        apiBase: state.apiBase,
        query
      });
    } else {
      mountOverview(app, { apiBase: state.apiBase, query });
    }
    activeView = 'overview';
    window.scrollTo({ top: 0 });
    return;
  }

  if (activeView === 'studio') {
    // 已在现场：若带 taskNo 且与当前任务不同，则切换到该任务接续观察。
    const resumeTaskNo = parseStudioTaskNo(query);
    if (resumeTaskNo && resumeTaskNo !== state.taskNo) {
      resumeTask(resumeTaskNo);
    }
    return;
  }
  mountStudio(parseStudioTaskNo(query));
  activeView = 'studio';
  window.scrollTo({ top: 0 });
}

function parseStudioTaskNo(query) {
  const params = new URLSearchParams(query);
  const taskNo = params.get('taskNo');
  return taskNo ? decodeURIComponent(taskNo) : '';
}

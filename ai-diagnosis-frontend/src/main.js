import anime from 'animejs/lib/anime.es.js';
import '@phosphor-icons/web/regular';
import { createEmptyInsightSummary, extractInsightSummary } from './insight-summary.js';
import { mountOverview } from './overview.js';
import { mountEventDetail } from './event-detail.js';
import { mountKnowledge } from './knowledge.js';
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
  'SQL_CAPTURE_WAITING',
  'SQL_CAPTURED',
  'SQL_CAPTURE_FAILED',
  'SQL_DATASOURCE_SELECTING',
  'SQL_DATASOURCE_SELECTED',
  'SQL_DATASOURCE_AMBIGUOUS',
  'SQL_EXPLAIN_START',
  'SQL_EXPLAIN_SUCCESS',
  'SQL_EXPLAIN_FAILED',
  'SQL_META_COLLECTING',
  'SQL_META_COLLECTED',
  'SQL_META_FAILED',
  'JOINT_REPORT_GENERATING',
  'JOINT_REPORT_GENERATED',
  'JOINT_REPORT_FAILED',
  'KNOWLEDGE_RETRIEVING',
  'KNOWLEDGE_RETRIEVED',
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
  SQL_CAPTURE_WAITING: '等待 SQL',
  SQL_CAPTURED: 'SQL 已捕获',
  SQL_CAPTURE_FAILED: 'SQL 捕获失败',
  SQL_DATASOURCE_SELECTING: '匹配数据源',
  SQL_DATASOURCE_SELECTED: '数据源已选择',
  SQL_DATASOURCE_AMBIGUOUS: '数据源歧义',
  SQL_EXPLAIN_START: 'SQL Explain',
  SQL_EXPLAIN_SUCCESS: 'Explain 成功',
  SQL_EXPLAIN_FAILED: 'Explain 失败',
  SQL_META_COLLECTING: '采集元数据',
  SQL_META_COLLECTED: '元数据完成',
  SQL_META_FAILED: '元数据失败',
  JOINT_REPORT_GENERATING: '联合分析',
  JOINT_REPORT_GENERATED: '联合报告',
  JOINT_REPORT_FAILED: '联合诊断失败',
  KNOWLEDGE_RETRIEVING: '检索知识库',
  KNOWLEDGE_RETRIEVED: '知识检索完成',
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
  SQL_CAPTURE_WAITING: { icon: 'ph-binoculars', tone: 'amber' },
  SQL_CAPTURED: { icon: 'ph-check-circle', tone: 'green' },
  SQL_CAPTURE_FAILED: { icon: 'ph-warning', tone: 'red' },
  SQL_DATASOURCE_SELECTING: { icon: 'ph-database', tone: 'cyan' },
  SQL_DATASOURCE_SELECTED: { icon: 'ph-check-circle', tone: 'green' },
  SQL_DATASOURCE_AMBIGUOUS: { icon: 'ph-warning-circle', tone: 'amber' },
  SQL_EXPLAIN_START: { icon: 'ph-database', tone: 'amber' },
  SQL_EXPLAIN_SUCCESS: { icon: 'ph-check-circle', tone: 'green' },
  SQL_EXPLAIN_FAILED: { icon: 'ph-warning', tone: 'red' },
  SQL_META_COLLECTING: { icon: 'ph-table', tone: 'cyan' },
  SQL_META_COLLECTED: { icon: 'ph-check-circle', tone: 'green' },
  SQL_META_FAILED: { icon: 'ph-warning', tone: 'red' },
  JOINT_REPORT_GENERATING: { icon: 'ph-brain', tone: 'violet' },
  JOINT_REPORT_GENERATED: { icon: 'ph-file-text', tone: 'green' },
  JOINT_REPORT_FAILED: { icon: 'ph-warning-octagon', tone: 'red' },
  KNOWLEDGE_RETRIEVING: { icon: 'ph-books', tone: 'cyan' },
  KNOWLEDGE_RETRIEVED: { icon: 'ph-book-open-text', tone: 'green' },
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

// ===== 场景驱动的诊断流程模板 =====
// 按 diagnoseType 定义候选阶段序列。每个阶段声明：id / type / label / icon / hint。
// type: PLAN(意图计划) / TOOL(Arthas 采样) / SQL(SQL 诊断) / REPORT(根因报告) / TERMINAL(终态)
// conditional='SQL' 的阶段初始不渲染，仅当 SQL 相关事件到来时动态插入主时间线。
// tools: 该 TOOL 阶段接受的工具名（用于把工具事件路由到正确阶段）。
const FLOW_TEMPLATES = {
  HIGH_CPU: [
    { id: 'intent', type: 'PLAN', label: '意图识别', icon: 'ph-crosshair', hint: '识别为 CPU 高' },
    { id: 'overview', type: 'TOOL', label: '现场快照', icon: 'ph-gauge', hint: 'dashboard / topThreads', tools: ['dashboard', 'topThreads'] },
    { id: 'thread', type: 'TOOL', label: '热点线程', icon: 'ph-stack', hint: 'thread 堆栈定位', tools: ['threadStack'] },
    { id: 'reason', type: 'REPORT', label: '根因推理', icon: 'ph-brain', hint: 'AI 归因' },
    { id: 'done', type: 'TERMINAL', label: '诊断完成', icon: 'ph-seal-check', hint: '等待验证修复' }
  ],
  MEMORY_ABNORMAL: [
    { id: 'intent', type: 'PLAN', label: '意图识别', icon: 'ph-crosshair', hint: '识别为内存异常' },
    { id: 'mem', type: 'TOOL', label: '内存采样', icon: 'ph-memory', hint: 'memory / dashboard / jvm', tools: ['memoryInfo', 'dashboard', 'jvmInfo'] },
    { id: 'reason', type: 'REPORT', label: '根因推理', icon: 'ph-brain', hint: 'AI 归因' },
    { id: 'done', type: 'TERMINAL', label: '诊断完成', icon: 'ph-seal-check', hint: '等待验证修复' }
  ],
  THREAD_BLOCKED: [
    { id: 'intent', type: 'PLAN', label: '意图识别', icon: 'ph-crosshair', hint: '识别为线程阻塞' },
    { id: 'overview', type: 'TOOL', label: '现场快照', icon: 'ph-gauge', hint: 'dashboard', tools: ['dashboard'] },
    { id: 'thread', type: 'TOOL', label: '阻塞线程', icon: 'ph-stack', hint: 'thread 堆栈/锁', tools: ['threadStack'] },
    { id: 'reason', type: 'REPORT', label: '根因推理', icon: 'ph-brain', hint: 'AI 归因' },
    { id: 'done', type: 'TERMINAL', label: '诊断完成', icon: 'ph-seal-check', hint: '等待验证修复' }
  ],
  SLOW_REQUEST: [
    { id: 'intent', type: 'PLAN', label: '意图识别', icon: 'ph-crosshair', hint: '识别为接口慢' },
    { id: 'trace', type: 'TOOL', label: '调用链追踪', icon: 'ph-git-branch', hint: 'trace Controller / Service', tools: ['traceMethod'] },
    { id: 'sql-watch', type: 'SQL', label: 'SQL 捕获', icon: 'ph-binoculars', hint: 'watch MyBatis/JDBC 还原 SQL', conditional: 'SQL', phase: 'WATCH' },
    { id: 'sql-explain', type: 'SQL', label: 'SQL 分析', icon: 'ph-database', hint: 'Explain + 表/索引元数据', conditional: 'SQL', phase: 'EXPLAIN' },
    { id: 'reason', type: 'REPORT', label: '根因推理', icon: 'ph-brain', hint: 'Java (+SQL) 联合归因' },
    { id: 'done', type: 'TERMINAL', label: '诊断完成', icon: 'ph-seal-check', hint: '等待验证修复' }
  ],
  UNKNOWN: [
    { id: 'intent', type: 'PLAN', label: '意图识别', icon: 'ph-crosshair', hint: '问题归类中' },
    { id: 'sample', type: 'TOOL', label: '现场采样', icon: 'ph-terminal-window', hint: 'Arthas 受控采样' },
    { id: 'reason', type: 'REPORT', label: '根因推理', icon: 'ph-brain', hint: 'AI 归因' },
    { id: 'done', type: 'TERMINAL', label: '诊断完成', icon: 'ph-seal-check', hint: '等待验证修复' }
  ]
};

// SSE 事件 → 流程阶段的路由表。node 为目标阶段 id；route 为动态路由函数；
// status 推进该阶段状态；collect 把事件证据收集到阶段；flag 控制特殊行为。
const EVENT_NODE_MAP = {
  TASK_CREATED: { node: 'intent', status: 'running' },
  INTENT_CLASSIFYING: { node: 'intent', status: 'running' },
  INTENT_CLASSIFIED: { node: 'intent', status: 'done', applyTemplate: true },
  PLAN_CREATED: { node: 'intent', status: 'done' },
  TOOL_CALL_START: { route: 'tool', status: 'running', collect: 'command' },
  TOOL_CALL_SUCCESS: { route: 'tool', status: 'done', collect: 'output' },
  TOOL_CALL_FAILED: { route: 'tool', status: 'failed', collect: 'error' },
  SQL_CAPTURE_WAITING: { node: 'sql-watch', status: 'running', collect: 'watchCmd' },
  SQL_CAPTURED: { node: 'sql-watch', status: 'done', collect: 'capture' },
  SQL_CAPTURE_FAILED: { node: 'sql-watch', status: 'failed' },
  SQL_DATASOURCE_SELECTING: { node: 'sql-explain', status: 'running', collect: 'datasource' },
  SQL_DATASOURCE_SELECTED: { node: 'sql-explain', status: 'running', collect: 'datasource' },
  SQL_DATASOURCE_AMBIGUOUS: { node: 'sql-explain', status: 'running', collect: 'datasource' },
  SQL_EXPLAIN_START: { node: 'sql-explain', status: 'running' },
  SQL_EXPLAIN_SUCCESS: { node: 'sql-explain', status: 'running', collect: 'explain' },
  SQL_EXPLAIN_FAILED: { node: 'sql-explain', status: 'failed' },
  SQL_META_COLLECTING: { node: 'sql-explain', status: 'running' },
  SQL_META_COLLECTED: { node: 'sql-explain', status: 'done', collect: 'metadata' },
  SQL_META_FAILED: { node: 'sql-explain', status: 'failed' },
  JOINT_REPORT_GENERATING: { node: 'reason', status: 'running' },
  JOINT_REPORT_GENERATED: { node: 'reason', status: 'done' },
  JOINT_REPORT_FAILED: { node: 'reason', status: 'failed' },
  KNOWLEDGE_RETRIEVING: { node: 'reason', status: 'running' },
  KNOWLEDGE_RETRIEVED: { node: 'reason', status: 'running' },
  AI_ANALYZING: { node: 'reason', status: 'running' },
  REPORT_GENERATED: { node: 'reason', status: 'done' },
  TASK_FINISHED: { node: 'done', status: 'done', completeAll: true },
  TASK_FAILED: { targetActive: true, status: 'failed' },
  TASK_INTERRUPTED: { targetActive: true, status: 'failed' }
};

const PASSIVE_EVENT_TYPES = new Set(['HEARTBEAT']);
const CURRENT_TASK_STORAGE_KEY = 'ai-diagnosis.currentTaskNo';

const DEMO_EVENTS = [
  ['TASK_CREATED', 'Agent 任务已创建，开始接管现场。', { appId: 'order-service', env: 'prod' }],
  ['INTENT_CLASSIFYING', '正在识别异常类型与采样目标。'],
  ['INTENT_CLASSIFIED', '识别为慢请求，准备追踪 Java 调用链。', {
    diagnoseType: 'SLOW_REQUEST',
    confidence: 0.91,
    reason: '目标接口响应时间持续升高'
  }],
  ['PLAN_CREATED', '已生成 Arthas 采样计划。', 'TOOL_CALLING'],
  ['TOOL_CALL_START', '执行 trace 追踪调用链耗时。', { command: 'trace com.example.OrderController createOrder -n 3', toolName: 'traceMethod' }],
  ['TOOL_CALL_SUCCESS', '调用链已采集，慢点定位到 OrderService#createOrder。', {
    command: 'trace com.example.OrderController createOrder -n 3',
    toolName: 'traceMethod',
    costMillis: 428,
    outputExcerpt: 'OrderService#createOrder cost=81.0%'
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
  // 统一的动态流程状态：按 diagnoseType 选模板，SQL 分支按事件动态插入。
  flow: createEmptyFlow(),
  events: [],
  commandRecords: [],
  reportMarkdown: '',
  insightSummary: createEmptyInsightSummary(),
  resumeDiagnoseType: '',
  observationState: '',
  taskStatus: '',
  restartAllowed: false,
  lastEventId: 0,
  seenEventIds: new Set(),
  lastResponse: null,
  eventSource: null,
  sqlDiagnosis: null,
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
              <strong data-progress-label>0 / 0 步骤完成</strong>
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

          <div class="graph-wrap" aria-label="诊断流程">
            <div class="graph-legend">
              <span><i class="legend-dot java"></i>意图/计划</span>
              <span><i class="legend-dot arthas"></i>Arthas 采样</span>
              <span><i class="legend-dot ai"></i>SQL 诊断</span>
              <span><i class="legend-dot done"></i>已完成</span>
            </div>
            <div class="diagnosis-flow" data-diagnosis-flow>
              <p class="flow-empty" data-flow-empty>启动诊断后，将根据问题类型动态展示诊断流程；若涉及 SQL，SQL 阶段会自动加入流程。</p>
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
            <div class="flow-runbook" data-flow-runbook>
              <p class="flow-empty">等待诊断意图识别后生成执行步骤。</p>
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
  downloadReport: null,
  diagnosisFlow: null,
  flowRunbook: null
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
  refs.diagnosisFlow = document.querySelector('[data-diagnosis-flow]');
  refs.flowRunbook = document.querySelector('[data-flow-runbook]');
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
    state.taskStatus = task.status || '';
    state.restartAllowed = Boolean(detail?.restartAllowed);
    state.sqlDiagnosis = detail?.latestSqlDiagnosis || null;
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
    renderFlow();
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
  events.forEach((event) => {
    pushEvent(event);
  });
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
      toolName: current[2]?.toolName || (current[0].startsWith('TOOL') ? 'Arthas' : ''),
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
    state.taskStatus = 'FINISHED';
    forgetCurrentTask();
    closeLiveSources(false);
    setConnection('SUCCESS', '诊断完成');
    refreshTaskDetail({ silent: true });
  }

  if (eventPayload.eventType === 'TASK_FAILED') {
    state.taskStatus = 'FAILED';
    forgetCurrentTask();
    closeLiveSources(false);
    setConnection('ERROR', '诊断失败');
    refreshTaskDetail({ silent: true });
  }

  if (eventPayload.eventType === 'TASK_INTERRUPTED') {
    state.taskStatus = 'INTERRUPTED';
    forgetCurrentTask();
    state.observationState = 'INTERRUPTED';
    state.restartAllowed = true;
    closeLiveSources(false);
    updateActionAvailability();
    setConnection('ERROR', '诊断已中断，后台执行已不存在');
  }

  if (eventPayload.eventType === 'JOINT_REPORT_GENERATED') {
    setConnection('RUNNING', '联合报告已生成，正在保存任务结果');
  }

  if (eventPayload.eventType === 'JOINT_REPORT_FAILED') {
    closeLiveSources(false);
    setConnection('ERROR', 'SQL 联合诊断失败，原 Java 报告已保留');
    refreshTaskDetail({ silent: true });
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

  const previousActive = state.flow.activeIndex;

  // 意图识别完成时，按 diagnoseType 选择流程模板（SQL 阶段待事件触发后动态插入）
  if (normalized.eventType === 'INTENT_CLASSIFIED' && normalized.data?.diagnoseType) {
    state.resumeDiagnoseType = normalized.data.diagnoseType;
    applyFlowTemplate(normalized.data.diagnoseType);
  }
  // 事件驱动流程推进（含 SQL 分支动态激活）
  applyEventToFlow(normalized);

  if (normalized.eventType === 'REPORT_GENERATED') {
    state.reportMarkdown = extractReportMarkdown(normalized.data) || state.reportMarkdown;
    state.insightSummary = extractInsightSummary(normalized.data);
    renderReport();
  }

  if (normalized.eventType === 'TASK_FINISHED') {
    state.taskStatus = 'FINISHED';
  }

  state.events = [normalized, ...state.events].slice(0, 18);
  renderEventDrivenState();
  animateEvent(state.flow.activeIndex !== previousActive ? state.flow.activeIndex : null);
}

async function refreshTaskDetail(options = {}) {
  if (!state.taskNo) {
    showNotice('先启动诊断或填写任务号。');
    return null;
  }

  const action = async () => {
    const detail = await requestJson(`${state.apiBase}/api/diagnose/tasks/${encodeURIComponent(state.taskNo)}/detail`);
    state.commandRecords = detail.commandRecords || [];
    state.taskStatus = detail.task?.status || state.taskStatus;
    state.resumeDiagnoseType = detail.task?.diagnoseType || state.resumeDiagnoseType;
    state.sqlDiagnosis = detail.latestSqlDiagnosis || null;
    renderFlow();
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

// ===== 场景驱动的动态流程：模板选择、SQL 分支动态插入、事件路由、渲染 =====

function createEmptyFlow() {
  return { templateKey: null, template: null, nodes: [], activeIndex: -1, sqlActivated: false };
}

function toRuntimeNode(stage) {
  return {
    id: stage.id,
    type: stage.type,
    label: stage.label,
    icon: stage.icon,
    hint: stage.hint,
    tools: stage.tools || null,
    conditional: stage.conditional || null,
    status: 'pending',
    events: [],
    evidence: {},
    startedAt: null,
    endedAt: null
  };
}

function applyFlowTemplate(diagnoseType) {
  const key = normalizeDiagnoseTypeKey(diagnoseType);
  const template = FLOW_TEMPLATES[key] || FLOW_TEMPLATES.UNKNOWN;
  state.flow = {
    templateKey: key,
    template,
    nodes: template.filter((stage) => !stage.conditional).map(toRuntimeNode),
    activeIndex: -1,
    sqlActivated: false
  };
}

function normalizeDiagnoseTypeKey(value) {
  const key = String(value || '').trim().toUpperCase();
  return FLOW_TEMPLATES[key] ? key : 'UNKNOWN';
}

// 第一个 SQL/JOINT 事件到来时，把模板里 conditional=SQL 的阶段按顺序插入到 reason 之前。
function maybeActivateSqlBranch(event) {
  if (state.flow.sqlActivated || !state.flow.template) return;
  const isSqlEvent = event.eventType?.startsWith('SQL_') || event.eventType?.startsWith('JOINT_');
  if (!isSqlEvent) return;
  state.flow.sqlActivated = true;
  const sqlStages = state.flow.template.filter((stage) => stage.conditional === 'SQL');
  let insertAt = state.flow.nodes.findIndex((node) => node.id === 'reason');
  if (insertAt < 0) insertAt = state.flow.nodes.length;
  sqlStages.forEach((stage, offset) => {
    state.flow.nodes.splice(insertAt + offset, 0, toRuntimeNode(stage));
  });
}

function applyEventToFlow(event) {
  if (!state.flow.template) return;
  maybeActivateSqlBranch(event);
  const mapping = EVENT_NODE_MAP[event.eventType];
  if (!mapping) return;

  let node;
  if (mapping.route === 'tool') {
    node = routeToolNode(event);
  } else if (mapping.targetActive) {
    node = state.flow.nodes[state.flow.activeIndex] || state.flow.nodes[state.flow.nodes.length - 1];
  } else {
    node = state.flow.nodes.find((item) => item.id === mapping.node);
  }
  if (!node) return;

  const eventTime = parseEventTime(event.time);
  if (!node.startedAt) node.startedAt = eventTime;
  if (mapping.status && mapping.status !== 'running') node.endedAt = eventTime;
  if (mapping.status) node.status = mapping.status;
  if (mapping.collect) collectNodeEvidence(node, mapping.collect, event);
  node.events.push({ eventType: event.eventType, message: event.message, time: event.time });

  // 流程单调推进：把更早的阶段标记完成
  const currentIndex = state.flow.nodes.indexOf(node);
  if (currentIndex >= 0) {
    for (let i = 0; i < currentIndex; i += 1) {
      const prev = state.flow.nodes[i];
      if (prev.status === 'pending') {
        prev.status = 'done';
        if (!prev.startedAt) prev.startedAt = eventTime;
        if (!prev.endedAt) prev.endedAt = eventTime;
      }
    }
    if (state.flow.activeIndex < currentIndex) state.flow.activeIndex = currentIndex;
  }

  if (mapping.completeAll) {
    state.flow.nodes.forEach((item) => {
      if (item.status === 'pending' || item.status === 'running') {
        item.status = 'done';
        if (!item.startedAt) item.startedAt = eventTime;
        if (!item.endedAt) item.endedAt = eventTime;
      }
    });
    state.flow.activeIndex = state.flow.nodes.length - 1;
  }
}

function routeToolNode(event) {
  const tool = event.toolName || inferToolFromCommand(event.command);
  const byTool = state.flow.nodes.find((node) => node.tools?.includes(tool));
  if (byTool) return byTool;
  const traceNode = state.flow.nodes.find((node) => node.id === 'trace');
  if (traceNode && event.command?.startsWith('trace ')) return traceNode;
  return state.flow.nodes.find((node) => node.type === 'TOOL');
}

function inferToolFromCommand(command) {
  if (!command) return '';
  const head = command.trim().split(/\s+/)[0];
  if (head === 'trace') return 'traceMethod';
  return head;
}

function collectNodeEvidence(node, kind, event) {
  const data = event.data && typeof event.data === 'object' ? event.data : {};
  if (kind === 'command') {
    node.evidence.command = event.command || data.command || node.evidence.command || '';
    node.evidence.requestNo = data.requestNo || node.evidence.requestNo || '';
  } else if (kind === 'output') {
    node.evidence.output = data.output || data.outputExcerpt || event.message || node.evidence.output || '';
    node.evidence.costMillis = Number.isFinite(Number(data.costMillis)) ? data.costMillis : node.evidence.costMillis;
    node.evidence.success = true;
  } else if (kind === 'error') {
    node.evidence.error = data.errorMessage || event.message || node.evidence.error || '';
    node.evidence.success = false;
  } else if (kind === 'watchCmd') {
    node.evidence.watchCommand = data.command || event.command || node.evidence.watchCommand || '';
  } else if (kind === 'capture') {
    node.evidence.captureOutput = data.output || event.message || node.evidence.captureOutput || '';
    node.evidence.watchCommand = data.command || event.command || node.evidence.watchCommand;
  } else if (kind === 'datasource') {
    const name = data.datasourceName;
    node.evidence.datasource = name
      ? `${name} · ${data.datasourceCode || ''}`
      : data.datasourceCode || event.message || node.evidence.datasource || '';
  } else if (kind === 'explain') {
    node.evidence.explainResult = data.explainResult || node.evidence.explainResult || '';
  } else if (kind === 'metadata') {
    // 元数据采集完成时，从 latestSqlDiagnosis 补全 explain/表结构等完整证据
    const record = state.sqlDiagnosis;
    if (record) {
      node.evidence.explainResult = record.explainResult || node.evidence.explainResult || '';
      node.evidence.tableMetaJson = record.tableMetaJson || '';
      node.evidence.indexMetaJson = record.indexMetaJson || '';
      node.evidence.tableStatsJson = record.tableStatsJson || '';
      node.evidence.originalSql = record.originalSql || node.evidence.originalSql || '';
      node.evidence.mainTableName = record.mainTableName || node.evidence.mainTableName || '';
      node.evidence.datasourceCode = record.datasourceCode || node.evidence.datasourceCode || '';
    }
  }
}

function renderFlow() {
  if (!refs.diagnosisFlow) return;
  const nodes = state.flow.nodes;
  if (!nodes.length) {
    refs.diagnosisFlow.innerHTML = '<p class="flow-empty">启动诊断后，将根据问题类型动态展示诊断流程；若涉及 SQL，SQL 阶段会自动加入流程。</p>';
    renderFlowRunbook();
    return;
  }
  refs.diagnosisFlow.innerHTML = `
    <ol class="flow-track">
      ${nodes.map((node, index) => flowNodeHtml(node, index)).join('')}
    </ol>
  `;
  renderFlowRunbook();
}

function flowNodeHtml(node, index) {
  const isActive = index === state.flow.activeIndex && node.status === 'running';
  const tones = { PLAN: 'java', TOOL: 'arthas', SQL: 'ai', REPORT: 'ai', TERMINAL: 'done' };
  const tone = tones[node.type] || 'java';
  const statusIcon = {
    pending: 'ph-circle',
    running: 'ph-spinner-gap',
    done: 'ph-check-circle',
    failed: 'ph-warning'
  }[node.status] || 'ph-circle';
  const evidenceHtml = flowEvidenceHtml(node);
  return `
    <li class="flow-step is-${node.status} ${isActive ? 'is-active' : ''}" data-flow-node="${index}" data-tone="${tone}">
      <div class="flow-step-head">
        <span class="flow-marker"><i class="ph ${escapeHtml(node.icon)}" aria-hidden="true"></i></span>
        <div class="flow-step-meta">
          <strong>${escapeHtml(node.label)}</strong>
          <small>${escapeHtml(node.hint || '')}</small>
        </div>
        <span class="flow-status"><i class="ph ${statusIcon}" aria-hidden="true"></i>${flowStatusLabel(node)}</span>
      </div>
      ${evidenceHtml ? `<div class="flow-evidence">${evidenceHtml}</div>` : ''}
    </li>
  `;
}

function flowStatusLabel(node) {
  if (node.status === 'running') return '进行中';
  if (node.status === 'done') return node.endedAt ? `完成 · ${formatDuration(node.startedAt, node.endedAt)}` : '完成';
  if (node.status === 'failed') return '失败';
  return '等待';
}

function flowEvidenceHtml(node) {
  const ev = node.evidence;
  const parts = [];
  if (ev.command) parts.push(flowEvidenceBlock('Arthas 命令', ev.command));
  if (ev.output) parts.push(flowEvidenceBlock('采样输出', truncateText(ev.output, 600)));
  if (ev.costMillis != null) parts.push(flowEvidenceBlock('耗时', `${ev.costMillis} ms`));
  if (ev.error) parts.push(flowEvidenceBlock('错误', ev.error));
  if (ev.watchCommand) parts.push(flowEvidenceBlock('Watch 命令', ev.watchCommand));
  if (ev.captureOutput) parts.push(flowEvidenceBlock('捕获 SQL', truncateText(ev.captureOutput, 600)));
  if (ev.datasource) parts.push(flowEvidenceBlock('数据源', ev.datasource));
  if (ev.originalSql) parts.push(flowEvidenceBlock('SQL 原文', ev.originalSql));
  if (ev.explainResult) parts.push(flowEvidenceBlock('MySQL Explain', prettyJson(ev.explainResult)));
  if (ev.tableMetaJson) parts.push(flowEvidenceBlock('字段结构', prettyJson(ev.tableMetaJson)));
  if (ev.indexMetaJson) parts.push(flowEvidenceBlock('索引信息', prettyJson(ev.indexMetaJson)));
  if (ev.tableStatsJson) parts.push(flowEvidenceBlock('表统计', prettyJson(ev.tableStatsJson)));
  if (node.events.length) {
    parts.push(`<details class="flow-event-log"><summary>阶段事件 (${node.events.length})</summary><ul>${
      node.events.map((e) => `<li><small>${escapeHtml(formatTime(e.time))}</small> ${escapeHtml(e.message || e.eventType)}</li>`).join('')
    }</ul></details>`);
  }
  return parts.join('');
}

function flowEvidenceBlock(label, value) {
  return `<article class="flow-evidence-block"><span>${escapeHtml(label)}</span><pre>${escapeHtml(value)}</pre></article>`;
}

function renderFlowRunbook() {
  if (!refs.flowRunbook) return;
  const nodes = state.flow.nodes;
  if (!nodes.length) {
    refs.flowRunbook.innerHTML = '<p class="flow-empty">等待诊断意图识别后生成执行步骤。</p>';
    return;
  }
  refs.flowRunbook.innerHTML = nodes.map((node, index) => `
    <div class="flow-runbook-step is-${node.status} ${index === state.flow.activeIndex ? 'is-active' : ''}">
      <span class="flow-runbook-index">${index + 1}</span>
      <div>
        <strong>${escapeHtml(node.label)}</strong>
        <small>${escapeHtml(node.hint || '')}</small>
      </div>
      <span class="flow-runbook-duration">${flowStatusLabel(node)}</span>
    </div>
  `).join('');
}

function prettyJson(value) {
  if (!value) return '';
  if (typeof value !== 'string') return JSON.stringify(value, null, 2);
  try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
}

function truncateText(value, max) {
  const text = String(value || '').trim();
  if (text.length <= max) return text;
  return `${text.slice(0, max)}…`;
}

function formatDuration(start, end) {
  if (!start || !end) return '';
  const ms = Math.max(0, end - start);
  if (ms < 1000) return '<1s';
  return `${Math.round(ms / 1000)}s`;
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
  renderFlow();
  renderRootCard();
  renderProgress();
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
  const total = state.flow.nodes.length;
  const doneCount = state.flow.nodes.filter((node) => node.status === 'done').length;
  const isComplete = total > 0 && doneCount >= total;
  const visibleCount = isComplete
    ? total
    : Math.min(doneCount + (state.flow.activeIndex >= 0 ? 1 : 0), total || 0);
  const progress = total > 0 ? Math.min(100, Math.round((visibleCount / total) * 100)) : 0;
  const progressLabel = document.querySelector('[data-progress-label]');
  const sessionStrip = document.querySelector('[data-session-strip]');
  progressLabel.classList.toggle('is-complete', isComplete);
  sessionStrip.classList.toggle('is-complete', isComplete);
  progressLabel.innerHTML = isComplete
    ? '<span class="status-icon" aria-hidden="true">✓</span>已完成分析'
    : total > 0
      ? `${visibleCount} / ${total} 步骤完成`
      : '等待启动';
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
  state.taskStatus = '';
  state.restartAllowed = false;
  state.lastEventId = 0;
  state.seenEventIds = new Set();
  state.sqlDiagnosis = null;
  state.flow = createEmptyFlow();
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

function parseEventTime(value) {
  const timestamp = new Date(value).getTime();
  return Number.isFinite(timestamp) ? timestamp : Date.now();
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

function animateEvent(stage) {
  if (reduceMotion) return;

  const flowStep = Number.isInteger(stage)
    ? document.querySelector(`[data-flow-node="${stage}"]`)
    : null;
  const runbookStep = Number.isInteger(stage)
    ? document.querySelector(`.flow-runbook-step:nth-child(${stage + 1})`)
    : null;

  [flowStep, runbookStep].filter(Boolean).forEach((target) => {
    anime.remove(target);
    anime({
      targets: target,
      scale: [0.98, 1],
      translateY: [6, 0],
      duration: 420,
      easing: 'easeOutCubic'
    });
  });

  const activeMarker = document.querySelector('.flow-step.is-active .flow-marker');
  if (activeMarker) {
    anime.remove(activeMarker);
    anime({
      targets: activeMarker,
      scale: [0.72, 1],
      opacity: [0.45, 1],
      duration: 520,
      easing: 'easeOutBack'
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

  if (root === 'knowledge') {
    mountKnowledge(app, { apiBase: state.apiBase, query });
    activeView = 'knowledge';
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

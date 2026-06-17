import anime from 'animejs/lib/anime.es.js';
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
  HEARTBEAT: '心跳',
  STREAM_ERROR: '连接异常',
  MESSAGE: '消息'
};

const STAGES = [
  {
    id: 'entry',
    label: '慢请求入口',
    role: 'HTTP Evidence',
    metric: 'P95 3.21s',
    hint: '业务症状'
  },
  {
    id: 'intent',
    label: '意图识别',
    role: 'AI Classifier',
    metric: 'High CPU',
    hint: '问题归类'
  },
  {
    id: 'plan',
    label: '诊断计划',
    role: 'Agent Planner',
    metric: '3 commands',
    hint: '采样路径'
  },
  {
    id: 'arthas',
    label: 'Arthas 采样',
    role: 'Tool Calling',
    metric: 'thread -n 5',
    hint: '现场证据'
  },
  {
    id: 'reasoning',
    label: '根因推理',
    role: 'AI Analyzer',
    metric: 'RAG Ready',
    hint: '证据归因'
  },
  {
    id: 'fix',
    label: '修复建议',
    role: 'Action Plan',
    metric: '待验证',
    hint: '回滚可控'
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
  STREAM_ERROR: 5
};

const PASSIVE_EVENT_TYPES = new Set(['HEARTBEAT']);

const PRESETS = [
  {
    label: 'CPU 高',
    question: '线上订单服务 CPU 持续升高，请结合 Arthas 采样定位热点线程和可疑方法。',
    metric: 'P95 3.21s',
    targetClass: 'com.example.order.controller.OrderController',
    targetMethod: 'createOrder',
    targetUri: '/api/orders'
  },
  {
    label: '接口慢',
    question: '订单查询接口响应变慢，请分析链路耗时、线程状态和可能的慢调用来源。',
    metric: 'P95 4.08s',
    targetClass: 'com.example.order.controller.OrderController',
    targetMethod: 'queryOrders',
    targetUri: '/api/orders/search'
  },
  {
    label: '线程阻塞',
    question: '支付回调偶发卡住，请检查阻塞线程、锁等待和业务调用栈。',
    metric: 'Block 37s',
    targetClass: 'com.example.pay.controller.CallbackController',
    targetMethod: 'callback',
    targetUri: '/api/pay/callback'
  }
];

const DEMO_EVENTS = [
  ['TASK_CREATED', 'Agent 任务已创建，开始接管现场。'],
  ['INTENT_CLASSIFYING', '正在识别异常类型与采样目标。'],
  ['INTENT_CLASSIFIED', '识别为高 CPU 与慢请求复合问题。'],
  ['PLAN_CREATED', '已生成 Arthas 采样计划。'],
  ['TOOL_CALL_START', '执行 thread -n 5 获取热点线程。'],
  ['TOOL_CALL_SUCCESS', '热点线程集中在 OrderService#createOrder。'],
  ['AI_ANALYZING', '正在融合线程栈、目标 URI 和历史报告。'],
  ['REPORT_GENERATED', '已生成根因与修复建议。'],
  ['TASK_FINISHED', '诊断完成，等待验证修复效果。']
];

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

const state = {
  apiBase: localStorage.getItem('diagnosis.apiBase') || 'http://localhost:9001',
  taskNo: localStorage.getItem('diagnosis.activeTaskNo') || '',
  appId: 'order-service',
  env: 'prod',
  userId: 'admin',
  mode: 'TOOL_CALLING',
  question: PRESETS[0].question,
  targetClass: PRESETS[0].targetClass,
  targetMethod: PRESETS[0].targetMethod,
  targetUri: PRESETS[0].targetUri,
  symptomMetric: PRESETS[0].metric,
  activeStage: 0,
  completedStages: new Set(),
  doneRunbook: new Set(),
  activeRunbook: 0,
  events: [],
  commandRecords: [],
  reportMarkdown: '',
  lastResponse: null,
  eventSource: null,
  demoTimer: null,
  loading: false,
  connection: 'READY'
};

document.querySelector('#app').innerHTML = `
  <div class="diagnosis-studio">
    <aside class="side-rail" aria-label="主导航">
      <a class="mark" href="/" aria-label="AI Arthas Diagnosis">A</a>
      <nav class="rail-nav">
        <button class="rail-button is-active" type="button" data-jump="map" aria-label="因果图谱">图</button>
        <button class="rail-button" type="button" data-jump="stream" aria-label="事件流">流</button>
        <button class="rail-button" type="button" data-jump="report" aria-label="诊断报告">报</button>
      </nav>
      <div class="rail-bottom">
        <span class="rail-state" data-connection-dot></span>
        <button class="rail-button" type="button" data-stop-stream aria-label="停止流">停</button>
      </div>
    </aside>

    <div class="workspace">
      <header class="topbar reveal">
        <div class="title-block">
          <span>AI + Arthas</span>
          <h1>性能因果图谱</h1>
          <p>Causal Map Studio</p>
        </div>

        <div class="top-fields">
          <label>
            <span>服务</span>
            <input id="appId" value="${escapeHtml(state.appId)}" autocomplete="off" />
          </label>
          <label>
            <span>环境</span>
            <input id="env" value="${escapeHtml(state.env)}" autocomplete="off" />
          </label>
          <label class="backend-field">
            <span>Backend</span>
            <input id="apiBase" value="${escapeHtml(state.apiBase)}" autocomplete="url" />
          </label>
        </div>

        <div class="session-strip" aria-live="polite">
          <div>
            <small>会话进度</small>
            <strong data-progress-label>0 / ${STAGES.length}</strong>
          </div>
          <div class="progress-track" aria-hidden="true">
            <span data-progress-bar></span>
          </div>
          <div>
            <small>当前任务</small>
            <strong data-task-label>${escapeHtml(state.taskNo || '未创建')}</strong>
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
              <button class="secondary-button" type="button" data-quick-ai>AI 快速分析</button>
              <button class="ghost-button" type="button" data-demo-flow>演示流</button>
            </div>
          </div>

          <div class="incident-card">
            <div class="incident-copy">
              <span>异常描述</span>
              <textarea id="question" rows="3">${escapeHtml(state.question)}</textarea>
            </div>
            <div class="preset-row">
              ${PRESETS.map((preset, index) => `
                <button class="preset-button ${index === 0 ? 'is-active' : ''}" type="button" data-preset="${index}">
                  <strong>${escapeHtml(preset.label)}</strong>
                  <span>${escapeHtml(preset.metric)}</span>
                </button>
              `).join('')}
            </div>
            <details class="target-details">
              <summary>目标范围</summary>
              <div class="target-grid">
                <label>
                  <span>URI</span>
                  <input id="targetUri" value="${escapeHtml(state.targetUri)}" autocomplete="off" />
                </label>
                <label>
                  <span>Class</span>
                  <input id="targetClass" value="${escapeHtml(state.targetClass)}" autocomplete="off" />
                </label>
                <label>
                  <span>Method</span>
                  <input id="targetMethod" value="${escapeHtml(state.targetMethod)}" autocomplete="off" />
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
                    <span>Evidence</span>
                    <strong data-stage-metric="${index}">${escapeHtml(stage.metric)}</strong>
                  </div>
                  <div class="node-shell">
                    <small>${escapeHtml(stage.role)}</small>
                    <h3>${escapeHtml(stage.label)}</h3>
                    <p>${escapeHtml(stage.hint)}</p>
                  </div>
                  ${index < STAGES.length - 1 ? `<span class="flow-edge" data-edge="${index}"></span>` : ''}
                </article>
              `).join('')}
            </div>
          </div>
        </section>

        <aside class="insight-panel reveal" aria-label="根因与建议">
          <section class="insight-card root-card">
            <div class="insight-heading">
              <p class="panel-label">根因与建议</p>
              <span data-status-pill>Ready</span>
            </div>
            <div class="root-orb" aria-hidden="true"></div>
            <h2 data-root-title>等待诊断事件</h2>
            <p data-root-copy>启动 Agent 后，SSE 事件会推动图谱节点、计划步骤和报告区域同步更新。</p>
          </section>

          <section id="stream" class="insight-card stream-card">
            <div class="insight-heading">
              <h3>流式事件</h3>
              <span data-event-count>0 events</span>
            </div>
            <div class="stream-list" data-stream-list></div>
          </section>

          <section id="report" class="insight-card report-card">
            <div class="insight-heading">
              <h3>AI 报告</h3>
              <button class="text-button" type="button" data-refresh-detail>刷新</button>
            </div>
            <article class="report-body" data-report-body></article>
          </section>
        </aside>
      </main>

      <section class="agent-plan reveal" aria-label="智能体执行计划">
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
            <article class="plan-step ${index === 0 ? 'is-active' : ''}" data-runbook="${index}">
              <span>${index + 1}</span>
              <strong>${escapeHtml(step.title)}</strong>
              <p>${escapeHtml(step.detail)}</p>
            </article>
          `).join('')}
        </div>
      </section>
    </div>
  </div>
`;

const refs = {
  apiBase: document.querySelector('#apiBase'),
  appId: document.querySelector('#appId'),
  env: document.querySelector('#env'),
  question: document.querySelector('#question'),
  targetUri: document.querySelector('#targetUri'),
  targetClass: document.querySelector('#targetClass'),
  targetMethod: document.querySelector('#targetMethod'),
  startAgent: document.querySelector('[data-start-agent]'),
  quickAi: document.querySelector('[data-quick-ai]'),
  demoFlow: document.querySelector('[data-demo-flow]'),
  stopStream: document.querySelector('[data-stop-stream]'),
  refreshDetail: document.querySelector('[data-refresh-detail]')
};

bindEvents();
renderAll();
runIntroAnimation();

function bindEvents() {
  document.querySelectorAll('[data-jump]').forEach((button) => {
    button.addEventListener('click', () => {
      document.querySelectorAll('[data-jump]').forEach((item) => item.classList.toggle('is-active', item === button));
      document.getElementById(button.dataset.jump)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  });

  document.querySelectorAll('[data-preset]').forEach((button) => {
    button.addEventListener('click', () => applyPreset(Number(button.dataset.preset), button));
  });

  [refs.apiBase, refs.appId, refs.env, refs.question, refs.targetUri, refs.targetClass, refs.targetMethod].forEach((input) => {
    input.addEventListener('change', readInputs);
  });

  refs.startAgent.addEventListener('click', startAgentDiagnosis);
  refs.quickAi.addEventListener('click', runQuickAi);
  refs.demoFlow.addEventListener('click', playDemoFlow);
  refs.stopStream.addEventListener('click', () => closeLiveSources(true));
  refs.refreshDetail.addEventListener('click', () => refreshReport());
}

function applyPreset(index, button) {
  const preset = PRESETS[index] || PRESETS[0];
  state.question = preset.question;
  state.targetClass = preset.targetClass;
  state.targetMethod = preset.targetMethod;
  state.targetUri = preset.targetUri;
  state.symptomMetric = preset.metric;

  refs.question.value = state.question;
  refs.targetClass.value = state.targetClass;
  refs.targetMethod.value = state.targetMethod;
  refs.targetUri.value = state.targetUri;

  document.querySelectorAll('[data-preset]').forEach((item) => item.classList.toggle('is-active', item === button));
  document.querySelector('[data-stage-metric="0"]').textContent = preset.metric;
  animatePress(button);
}

function readInputs() {
  state.apiBase = normalizeApiBase(refs.apiBase.value);
  state.appId = refs.appId.value.trim() || 'order-service';
  state.env = refs.env.value.trim() || 'prod';
  state.question = refs.question.value.trim();
  state.targetUri = refs.targetUri.value.trim();
  state.targetClass = refs.targetClass.value.trim();
  state.targetMethod = refs.targetMethod.value.trim();
  refs.apiBase.value = state.apiBase;
  localStorage.setItem('diagnosis.apiBase', state.apiBase);
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

    setTaskNo(response.taskNo);
    pushEvent({
      taskNo: response.taskNo,
      eventType: 'TASK_CREATED',
      message: '诊断任务已创建，正在等待 Agent 事件流。',
      success: true,
      time: new Date().toISOString()
    });
    connectStream(response.streamUrl);
    renderResponse(response);
    return response;
  });
}

async function runQuickAi() {
  readInputs();

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
    pushEvent({ eventType: 'TASK_CREATED', message: 'AI 快速诊断任务已创建。', success: true });
    pushEvent({ eventType: 'REPORT_GENERATED', message: 'AI 诊断报告已返回。', success: true, data: response });
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
      command: current[0] === 'TOOL_CALL_SUCCESS' ? 'thread -n 5' : '',
      toolName: current[0].startsWith('TOOL') ? 'Arthas' : '',
      success: !current[0].includes('FAILED'),
      time: new Date().toISOString(),
      data: current[0] === 'REPORT_GENERATED'
        ? { reportMarkdown: demoReport() }
        : null
    });
    index += 1;
  }, 720);
}

function connectStream(streamUrl) {
  if (!state.taskNo) return;

  const url = resolveStreamUrl(streamUrl);
  const source = new EventSource(url);
  state.eventSource = source;
  setConnection('RUNNING', 'SSE 已连接');

  STREAM_EVENT_TYPES.forEach((eventType) => {
    source.addEventListener(eventType, (event) => handleSseEvent(eventType, event));
  });

  source.onmessage = (event) => handleSseEvent('MESSAGE', event);
  source.onerror = () => {
    if (state.eventSource !== source) return;
    pushEvent({
      eventType: 'STREAM_ERROR',
      message: 'SSE 连接异常或已关闭。',
      success: false,
      time: new Date().toISOString()
    });
    closeLiveSources(false);
    setConnection('ERROR', 'SSE 已断开');
  };
}

function handleSseEvent(eventType, event) {
  const payload = safeJsonParse(event.data || '{}');
  const eventPayload = {
    ...payload,
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
    renderReport();
  }

  if (eventPayload.eventType === 'TASK_FINISHED') {
    closeLiveSources(false);
    setConnection('SUCCESS', '诊断完成');
    refreshTaskDetail({ silent: true });
  }

  if (eventPayload.eventType === 'TASK_FAILED') {
    closeLiveSources(false);
    setConnection('ERROR', '诊断失败');
    refreshTaskDetail({ silent: true });
  }
}

function pushEvent(event) {
  const normalized = {
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

  if (runbookIndex >= 0) {
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
  renderTimeline();
  renderRootCard();
  renderProgress();
}

function renderMapState() {
  document.querySelectorAll('[data-stage]').forEach((node) => {
    const index = Number(node.dataset.stage);
    node.classList.toggle('is-active', index === state.activeStage);
    node.classList.toggle('is-done', state.completedStages.has(index));
    node.classList.toggle('is-error', state.events[0]?.eventType === 'TASK_FAILED' && index === state.activeStage);
  });

  document.querySelectorAll('[data-edge]').forEach((edge) => {
    const index = Number(edge.dataset.edge);
    edge.classList.toggle('is-hot', index < state.activeStage);
  });
}

function renderRunbook() {
  document.querySelectorAll('[data-runbook]').forEach((step) => {
    const index = Number(step.dataset.runbook);
    step.classList.toggle('is-active', index === state.activeRunbook);
    step.classList.toggle('is-done', state.doneRunbook.has(index));
  });
}

function renderTimeline() {
  const target = document.querySelector('[data-stream-list]');
  document.querySelector('[data-event-count]').textContent = `${state.events.length} events`;

  if (!state.events.length) {
    target.innerHTML = `
      <div class="empty-state">
        <strong>等待 SSE</strong>
        <span>启动诊断后，事件会像电流一样推进图谱。</span>
      </div>
    `;
    return;
  }

  target.innerHTML = state.events.map((event) => {
    const label = EVENT_LABELS[event.eventType] || event.eventType;
    const command = [event.toolName, event.command].filter(Boolean).join(' / ');
    const tone = event.success === false ? 'is-bad' : event.success === true ? 'is-good' : '';

    return `
      <article class="stream-item ${tone}">
        <div>
          <strong>${escapeHtml(label)}</strong>
          <time>${escapeHtml(formatTime(event.time))}</time>
        </div>
        <p>${escapeHtml(event.message || command || '收到事件。')}</p>
        ${command ? `<small>${escapeHtml(command)}</small>` : ''}
      </article>
    `;
  }).join('');
}

function renderRootCard() {
  const latest = state.events[0];
  const statusText = state.connection === 'RUNNING'
    ? '分析中'
    : state.connection === 'SUCCESS'
      ? '已完成'
      : state.connection === 'ERROR'
        ? '需检查'
        : 'Ready';

  document.querySelector('[data-status-pill]').textContent = statusText;

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
  const target = document.querySelector('[data-report-body]');

  if (!state.reportMarkdown) {
    target.innerHTML = `
      <div class="report-empty">
        <strong>暂无报告</strong>
        <p>运行 AI 快速分析，或等待 Agent 生成 REPORT_GENERATED 事件。</p>
      </div>
    `;
    return;
  }

  target.innerHTML = renderMarkdownPreview(state.reportMarkdown);
}

function renderProgress() {
  const doneCount = Math.max(state.completedStages.size, state.activeStage);
  const progress = Math.min(100, Math.round(((doneCount + (state.events.length ? 1 : 0)) / STAGES.length) * 100));
  document.querySelector('[data-progress-label]').textContent = `${Math.min(doneCount + 1, STAGES.length)} / ${STAGES.length}`;
  document.querySelector('[data-progress-bar]').style.width = `${progress}%`;
  document.querySelector('[data-task-label]').textContent = state.taskNo || '未创建';
  document.querySelector('[data-cost-label]').textContent = state.events[0] ? EVENT_LABELS[state.events[0].eventType] || state.events[0].eventType : '等待启动';
}

function renderResponse(response) {
  state.lastResponse = response;
}

function setTaskNo(taskNo) {
  state.taskNo = taskNo || '';
  if (state.taskNo) {
    localStorage.setItem('diagnosis.activeTaskNo', state.taskNo);
  }
  renderProgress();
}

function setLoading(loading, label = '') {
  state.loading = loading;
  [refs.startAgent, refs.quickAi, refs.demoFlow, refs.refreshDetail].forEach((button) => {
    button.disabled = loading;
  });
  if (loading) setConnection('RUNNING', `${label}中`);
}

function setConnection(connection, text) {
  state.connection = connection;
  document.querySelector('[data-connection-dot]').dataset.state = connection.toLowerCase();
  document.querySelector('[data-status-pill]').textContent = text || connection;
}

function resetRun() {
  closeLiveSources(false);
  state.events = [];
  state.commandRecords = [];
  state.reportMarkdown = '';
  state.completedStages = new Set();
  state.doneRunbook = new Set();
  state.activeStage = 0;
  state.activeRunbook = 0;
  renderAll();
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

function normalizeApiBase(value) {
  return String(value || 'http://localhost:9001').trim().replace(/\/+$/, '');
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

function formatTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value || '').slice(0, 19);
  return date.toLocaleTimeString('zh-CN', { hour12: false });
}

function renderMarkdownPreview(markdown) {
  const lines = String(markdown || '').split('\n');
  let listOpen = false;
  const html = [];

  lines.forEach((line) => {
    const text = line.trim();
    if (!text) {
      if (listOpen) {
        html.push('</ul>');
        listOpen = false;
      }
      return;
    }

    if (text.startsWith('## ')) {
      if (listOpen) {
        html.push('</ul>');
        listOpen = false;
      }
      html.push(`<h4>${escapeHtml(text.slice(3))}</h4>`);
      return;
    }

    if (text.startsWith('# ')) {
      if (listOpen) {
        html.push('</ul>');
        listOpen = false;
      }
      html.push(`<h4>${escapeHtml(text.slice(2))}</h4>`);
      return;
    }

    if (text.startsWith('- ')) {
      if (!listOpen) {
        html.push('<ul>');
        listOpen = true;
      }
      html.push(`<li>${escapeHtml(text.slice(2))}</li>`);
      return;
    }

    if (listOpen) {
      html.push('</ul>');
      listOpen = false;
    }
    html.push(`<p>${escapeHtml(text)}</p>`);
  });

  if (listOpen) html.push('</ul>');
  return html.join('');
}

function demoReport() {
  return [
    '# 诊断结论',
    '热点线程集中在订单创建链路，业务方法占用时间异常。',
    '',
    '## 建议',
    '- 先对 OrderService#createOrder 增加限流和慢调用日志。',
    '- 复查下游库存接口超时配置。',
    '- 修复后再次运行同一任务验证 P95 是否回落。'
  ].join('\n');
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
  const streamItem = document.querySelector('.stream-item');
  const planStep = runbookChanged ? document.querySelector(`[data-runbook="${state.activeRunbook}"]`) : null;
  const edge = Number.isInteger(stage) && stage > 0 ? document.querySelector(`[data-edge="${stage - 1}"]`) : null;

  [node, streamItem, planStep].filter(Boolean).forEach((target) => {
    anime.remove(target);
    anime({
      targets: target,
      scale: [0.98, 1],
      translateY: [6, 0],
      duration: 420,
      easing: 'easeOutCubic'
    });
  });

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

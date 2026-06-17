import anime from 'animejs/lib/anime.es.js';
import './styles.css';

const COMMANDS = [
  { type: 'dashboard', label: 'Dashboard', hint: 'dashboard -n 1' },
  { type: 'thread', label: 'Thread', hint: 'thread' },
  { type: 'topThread', label: 'Top thread', hint: 'thread -n 5' },
  { type: 'threadBlock', label: 'Thread block', hint: 'thread -b' },
  { type: 'jvm', label: 'JVM', hint: 'jvm' },
  { type: 'memory', label: 'Memory', hint: 'memory' }
];

const DIAGNOSE_TYPES = [
  'HIGH_CPU',
  'SLOW_REQUEST',
  'THREAD_BLOCKED',
  'MEMORY_ABNORMAL',
  'UNKNOWN'
];

const AGENT_MODES = [
  'TOOL_CALLING',
  'RULE_FIRST'
];

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

const ACCEPTANCE_ITEMS = [
  { id: 'agent-start-api', label: 'POST /api/agent/diagnose/start 可用' },
  { id: 'agent-tool-mode', label: 'Agent 支持 TOOL_CALLING 模式' },
  { id: 'sse-stream', label: 'GET /api/diagnose/tasks/{taskNo}/stream 可用' },
  { id: 'sse-task-created', label: 'SSE 推送 TASK_CREATED' },
  { id: 'sse-intent', label: 'SSE 推送意图识别事件' },
  { id: 'sse-tool-start', label: 'SSE 推送 TOOL_CALL_START' },
  { id: 'sse-tool-result', label: 'SSE 推送工具调用结果' },
  { id: 'sse-report', label: 'SSE 推送报告生成事件' },
  { id: 'sse-finished', label: 'SSE 推送任务完成或失败' },
  { id: 'ai-api', label: 'POST /api/ai/diagnose 可用' },
  { id: 'ai-intent', label: 'AI 已识别受控 diagnoseType' },
  { id: 'ai-create-task', label: '智能诊断自动创建 diagnose_task' },
  { id: 'ai-run-rule', label: '智能诊断复用固定规则流程' },
  { id: 'ai-report', label: 'AI 已生成 Markdown 诊断报告' },
  { id: 'report-schema', label: 'diagnose_report 表可用', manual: true },
  { id: 'report-query', label: 'GET /api/ai/diagnose/{taskNo}/report 可用' },
  { id: 'report-regenerate', label: '报告可重新生成' },
  { id: 'schema-task', label: 'diagnose_task 表及目标字段可用', manual: true },
  { id: 'schema-record', label: 'arthas_command_record.task_no 可用', manual: true },
  { id: 'create-api', label: 'POST /api/diagnose/tasks 返回 taskNo' },
  { id: 'unique-task-no', label: 'taskNo 已生成' },
  { id: 'run-api', label: 'POST /api/diagnose/tasks/{taskNo}/run 可用' },
  { id: 'plan-high-cpu', label: 'HIGH_CPU 执行 dashboard + thread -n 5' },
  { id: 'plan-memory', label: 'MEMORY_ABNORMAL 执行 memory + dashboard + jvm' },
  { id: 'plan-thread', label: 'THREAD_BLOCKED 执行 dashboard + thread + thread -b' },
  { id: 'plan-slow', label: 'SLOW_REQUEST 执行受限 trace' },
  { id: 'status-finished', label: '成功后任务状态 FINISHED' },
  { id: 'status-failed', label: '失败后任务状态 FAILED 且有 error_message' },
  { id: 'execute-task-no', label: '/api/arthas/execute 仍支持 taskNo' },
  { id: 'record-task-no', label: '命令记录可按 taskNo 查询' },
  { id: 'detail-task', label: '详情返回诊断任务' },
  { id: 'detail-records', label: '详情返回命令链路和结论' },
  { id: 'missing-task-no', label: '不存在的 taskNo 返回明确错误' },
  { id: 'empty-task-no', label: 'taskNo 为空仍可执行单命令' }
];

const state = {
  commandType: 'dashboard',
  loading: false,
  activeTaskNo: localStorage.getItem('diagnosis.activeTaskNo') || '',
  lastResponse: null,
  taskDetail: null,
  reportMarkdown: '',
  history: [],
  sseEvents: [],
  eventSource: null,
  checks: new Set()
};

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const storedApiBase = localStorage.getItem('diagnosis.apiBase') || 'http://localhost:9001';

document.querySelector('#app').innerHTML = `
  <main class="shell">
    <header class="topbar reveal">
      <a class="brand" href="/" aria-label="AI Diagnosis Console 首页">
        <span class="brand-mark" aria-hidden="true">A</span>
        <span>AI Diagnosis</span>
      </a>
      <div class="service-state" aria-live="polite">
        <span class="state-dot" data-state-dot></span>
        <span data-service-state>等待验收</span>
      </div>
    </header>

    <section class="workspace acceptance-workspace">
      <section class="control-panel task-panel reveal" aria-labelledby="task-title">
        <div class="panel-heading compact-heading">
          <p class="panel-kicker">Phase 5 / 6</p>
          <h1 id="task-title">智能诊断验收</h1>
        </div>

        <form id="task-form" class="diagnosis-form">
          <div class="field">
            <label for="apiBase">后端地址</label>
            <input id="apiBase" name="apiBase" value="${escapeHtml(storedApiBase)}" autocomplete="url" />
          </div>
          <div class="field-row">
            <div class="field">
              <label for="appId">App ID</label>
              <input id="appId" name="appId" value="order-service" autocomplete="off" />
            </div>
            <div class="field compact">
              <label for="env">环境</label>
              <input id="env" name="env" value="test" autocomplete="off" />
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label for="userId">用户</label>
              <input id="userId" name="userId" value="admin" autocomplete="off" />
            </div>
            <div class="field compact">
              <label for="diagnoseType">诊断类型</label>
              <select id="diagnoseType" name="diagnoseType">
                ${DIAGNOSE_TYPES.map((type) => `<option value="${type}">${type}</option>`).join('')}
              </select>
            </div>
          </div>
          <div class="field">
            <label for="mode">Agent 模式</label>
            <select id="mode" name="mode">
              ${AGENT_MODES.map((mode) => `<option value="${mode}">${mode}</option>`).join('')}
            </select>
          </div>
          <div class="field">
            <label for="question">问题</label>
            <textarea id="question" name="question" rows="3">CPU 很高，帮我分析一下</textarea>
          </div>
          <div class="field">
            <label for="targetUri">目标 URI</label>
            <input id="targetUri" name="targetUri" placeholder="/api/order/create" autocomplete="off" />
          </div>
          <div class="field-row">
            <div class="field">
              <label for="targetClass">目标类</label>
              <input id="targetClass" name="targetClass" value="com.example.order.controller.OrderController" autocomplete="off" />
            </div>
            <div class="field">
              <label for="targetMethod">目标方法</label>
              <input id="targetMethod" name="targetMethod" value="createOrder" autocomplete="off" />
            </div>
          </div>
          <div class="action-row">
            <button class="primary-button" type="button" data-agent-diagnose-button>Agent 实时诊断</button>
            <button class="primary-button" type="button" data-ai-diagnose-button>AI 智能诊断</button>
            <button class="primary-button" type="submit" data-create-button>创建任务</button>
            <button class="primary-button" type="button" data-run-button>执行规则诊断</button>
            <button class="secondary-button" type="button" data-refresh-button>查询详情</button>
            <button class="secondary-button" type="button" data-report-button>查询报告</button>
            <button class="secondary-button" type="button" data-regenerate-report-button>重生成报告</button>
            <button class="secondary-button" type="button" data-finish-button>标记完成</button>
          </div>
        </form>
      </section>

      <section class="visual-panel command-panel reveal" aria-labelledby="command-title">
        <div class="section-head">
          <div>
            <p class="panel-kicker">Command audit</p>
            <h2 id="command-title">命令归属</h2>
          </div>
          <span class="status-pill" data-active-task-pill>${state.activeTaskNo ? escapeHtml(state.activeTaskNo) : '未创建'}</span>
        </div>

        <div class="field">
          <label for="taskNo">当前 taskNo</label>
          <input id="taskNo" name="taskNo" value="${escapeHtml(state.activeTaskNo)}" autocomplete="off" placeholder="创建任务后自动填入" />
        </div>

        <fieldset class="command-field">
          <legend>命令类型</legend>
          <div class="command-grid" data-command-grid>
            ${COMMANDS.map((command) => `
              <button
                class="command-button ${command.type === state.commandType ? 'is-active' : ''}"
                type="button"
                data-command="${command.type}"
                aria-pressed="${command.type === state.commandType}"
              >
                <span>${command.label}</span>
                <small>${command.hint}</small>
              </button>
            `).join('')}
          </div>
        </fieldset>

        <div class="action-row">
          <button class="primary-button" type="button" data-execute-task-button>带 taskNo 执行</button>
          <button class="secondary-button" type="button" data-execute-free-button>不带 taskNo</button>
          <button class="danger-button" type="button" data-missing-task-button>测试不存在 taskNo</button>
        </div>

        <div class="stream-panel" aria-live="polite">
          <div class="stream-head">
            <div>
              <p class="panel-kicker">Live stream</p>
              <h2>实时事件</h2>
            </div>
            <div class="stream-actions">
              <span class="status-pill" data-stream-count>0 条</span>
              <button class="text-button" type="button" data-disconnect-stream-button disabled>断开</button>
            </div>
          </div>
          <div class="stream-list" data-stream-list>
            <p class="subtle">启动 Agent 实时诊断后，这里会显示 SSE 事件。</p>
          </div>
        </div>
      </section>

      <section class="detail-panel reveal" aria-labelledby="detail-title">
        <div class="section-head">
          <div>
            <p class="panel-kicker">Task detail</p>
            <h2 id="detail-title">任务详情</h2>
          </div>
          <button class="text-button" type="button" data-copy-task-button disabled>复制 taskNo</button>
        </div>
        <div class="detail-body" data-detail-body>
          ${renderEmptyDetail()}
        </div>
      </section>

      <section class="result-panel reveal" aria-labelledby="result-title">
        <div class="result-head">
          <div>
            <p class="panel-kicker">Last response</p>
            <h2 id="result-title">接口响应</h2>
          </div>
          <button class="text-button" type="button" data-copy-button disabled>复制 JSON</button>
        </div>
        <div class="result-body" data-result-body>
          <div class="empty-state">
            <span class="empty-line"></span>
            <p>执行验收动作后，响应会显示在这里。</p>
          </div>
        </div>
      </section>

      <aside class="history-panel checklist-panel reveal" aria-labelledby="checklist-title">
        <div class="section-head">
          <h2 id="checklist-title">验收项</h2>
          <span class="status-pill" data-check-count>0/${ACCEPTANCE_ITEMS.length}</span>
        </div>
        <div data-checklist class="check-list"></div>
        <div class="history-divider"></div>
        <h2 class="history-title">最近动作</h2>
        <div data-history-list class="history-list">
          <p class="subtle">暂无记录</p>
        </div>
      </aside>
    </section>
  </main>
`;

const taskForm = document.querySelector('#task-form');
const taskNoInput = document.querySelector('#taskNo');
const commandButtons = Array.from(document.querySelectorAll('[data-command]'));
const agentDiagnoseButton = document.querySelector('[data-agent-diagnose-button]');
const aiDiagnoseButton = document.querySelector('[data-ai-diagnose-button]');
const createButton = document.querySelector('[data-create-button]');
const runButton = document.querySelector('[data-run-button]');
const refreshButton = document.querySelector('[data-refresh-button]');
const reportButton = document.querySelector('[data-report-button]');
const regenerateReportButton = document.querySelector('[data-regenerate-report-button]');
const finishButton = document.querySelector('[data-finish-button]');
const executeTaskButton = document.querySelector('[data-execute-task-button]');
const executeFreeButton = document.querySelector('[data-execute-free-button]');
const missingTaskButton = document.querySelector('[data-missing-task-button]');
const copyButton = document.querySelector('[data-copy-button]');
const copyTaskButton = document.querySelector('[data-copy-task-button]');
const disconnectStreamButton = document.querySelector('[data-disconnect-stream-button]');

renderChecklist();
runIntroAnimation();

commandButtons.forEach((button) => {
  button.addEventListener('click', () => {
    setCommand(button.dataset.command);
    animateButton(button);
  });
});

taskForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  await createTask();
});

agentDiagnoseButton.addEventListener('click', () => runAgentDiagnosis());
aiDiagnoseButton.addEventListener('click', () => runAiDiagnosis());
refreshButton.addEventListener('click', () => refreshDetail());
reportButton.addEventListener('click', () => queryReport());
regenerateReportButton.addEventListener('click', () => regenerateReport());
runButton.addEventListener('click', () => runDiagnosis());
finishButton.addEventListener('click', () => finishTask());
executeTaskButton.addEventListener('click', () => executeCommand({ mode: 'task' }));
executeFreeButton.addEventListener('click', () => executeCommand({ mode: 'free' }));
missingTaskButton.addEventListener('click', () => executeCommand({ mode: 'missing' }));
disconnectStreamButton.addEventListener('click', () => closeEventStream(true));

taskNoInput.addEventListener('input', () => {
  setActiveTaskNo(taskNoInput.value.trim(), false);
});

copyButton.addEventListener('click', async () => {
  if (!state.lastResponse) return;
  await navigator.clipboard.writeText(JSON.stringify(state.lastResponse, null, 2));
  flashButton(copyButton, '已复制', '复制 JSON');
});

copyTaskButton.addEventListener('click', async () => {
  if (!state.activeTaskNo) return;
  await navigator.clipboard.writeText(state.activeTaskNo);
  flashButton(copyTaskButton, '已复制', '复制 taskNo');
});

async function runAgentDiagnosis() {
  const values = getFormValues();
  closeEventStream(false);
  resetStreamEvents();

  await runAction('Agent 实时诊断', async () => {
    const response = await requestJson(`${values.apiBase}/api/agent/diagnose/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        appId: values.appId,
        env: values.env,
        userId: values.userId,
        question: values.question,
        targetUri: values.targetUri,
        targetClass: values.targetClass,
        targetMethod: values.targetMethod,
        mode: values.mode
      })
    });
    setActiveTaskNo(response.taskNo, true);
    markChecks(['agent-start-api', 'schema-task']);
    if (values.mode === 'TOOL_CALLING') {
      markChecks(['agent-tool-mode']);
    }
    renderResponse(response, 'Agent 已启动');
    connectDiagnoseStream(values.apiBase, response.taskNo);
    return response;
  });
}

async function runAiDiagnosis() {
  const values = getFormValues();
  await runAction('AI 智能诊断', async () => {
    const response = await requestJson(`${values.apiBase}/api/ai/diagnose`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        appId: values.appId,
        env: values.env,
        userId: values.userId,
        question: values.question,
        targetUri: values.targetUri,
        targetClass: values.targetClass,
        targetMethod: values.targetMethod
      })
    });
    setActiveTaskNo(response.taskNo, true);
    markAiChecks(response);
    renderReportResponse(response, response.status === 'FINISHED' ? '智能诊断完成' : '智能诊断失败');
    await refreshDetail({ silent: true });
    return response;
  });
}

async function createTask() {
  const values = getFormValues();
  await runAction('创建任务', async () => {
    const response = await requestJson(`${values.apiBase}/api/diagnose/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        appId: values.appId,
        env: values.env,
        userId: values.userId,
        question: values.question,
        diagnoseType: values.diagnoseType,
        targetUri: values.targetUri,
        targetClass: values.targetClass,
        targetMethod: values.targetMethod
      })
    });
    setActiveTaskNo(response.taskNo, true);
    markChecks(['create-api', 'unique-task-no', 'schema-task']);
    renderResponse(response, '创建成功');
    await refreshDetail({ silent: true });
    return response;
  });
}

async function runDiagnosis() {
  const taskNo = taskNoInput.value.trim();
  const values = getFormValues();
  if (!taskNo) {
    renderResponse({ message: '请先创建或填写 taskNo' }, '缺少 taskNo');
    return;
  }

  await runAction('执行规则诊断', async () => {
    const response = await requestJson(`${values.apiBase}/api/diagnose/tasks/${encodeURIComponent(taskNo)}/run`, {
      method: 'POST'
    });
    markRunChecks(values.diagnoseType, response);
    renderResponse(response, response.status === 'FINISHED' ? '诊断完成' : '诊断失败');
    await refreshDetail({ silent: true });
    return response;
  });
}

async function executeCommand({ mode }) {
  const values = getFormValues();
  const commandType = state.commandType;
  const taskNo = mode === 'missing' ? 'DIAG-NOT-FOUND' : taskNoInput.value.trim();
  const label = mode === 'free'
    ? '不带 taskNo 执行'
    : mode === 'missing'
      ? '不存在 taskNo'
      : '带 taskNo 执行';

  await runAction(label, async () => {
    const payload = {
      appId: values.appId,
      env: values.env,
      commandType
    };

    if (mode !== 'free') {
      payload.taskNo = taskNo;
    }

    try {
      const response = await requestJson(`${values.apiBase}/api/arthas/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (mode === 'task') {
        markChecks(['execute-task-no', 'schema-record']);
        await refreshDetail({ silent: true });
      }
      if (mode === 'free') {
        markChecks(['empty-task-no']);
      }
      renderResponse(response, response.success ? '命令完成' : '命令失败');
      return response;
    } catch (error) {
      const failure = {
        code: error.code || 'REQUEST_ERROR',
        message: error.message,
        taskNo: payload.taskNo,
        commandType
      };
      if (mode === 'missing' && error.message.includes('诊断任务不存在')) {
        markChecks(['missing-task-no']);
      }
      renderResponse(failure, '请求失败');
      return failure;
    }
  });
}

async function refreshDetail(options = {}) {
  const taskNo = taskNoInput.value.trim();
  const values = getFormValues();
  if (!taskNo) {
    renderResponse({ message: '请先创建或填写 taskNo' }, '缺少 taskNo');
    return null;
  }

  const action = async () => {
    const response = await requestJson(`${values.apiBase}/api/diagnose/tasks/${encodeURIComponent(taskNo)}/detail`);
    state.taskDetail = response;
    renderTaskDetail(response);
    const records = response.commandRecords || [];
    markChecks(['detail-task']);
    if (records.length > 0) {
      markChecks(['detail-records', 'record-task-no']);
    }
    if (!options.silent) {
      renderResponse(response, '详情已更新');
    }
    return response;
  };

  return options.silent ? action() : runAction('查询详情', action);
}

async function queryReport() {
  const taskNo = taskNoInput.value.trim();
  const values = getFormValues();
  if (!taskNo) {
    renderResponse({ message: '请先创建或填写 taskNo' }, '缺少 taskNo');
    return null;
  }

  await runAction('查询报告', async () => {
    const response = await requestJson(`${values.apiBase}/api/ai/diagnose/${encodeURIComponent(taskNo)}/report`);
    markChecks(['report-query', 'report-schema']);
    renderReportResponse(response, '报告已加载');
    return response;
  });
}

async function regenerateReport() {
  const taskNo = taskNoInput.value.trim();
  const values = getFormValues();
  if (!taskNo) {
    renderResponse({ message: '请先创建或填写 taskNo' }, '缺少 taskNo');
    return null;
  }

  await runAction('重生成报告', async () => {
    const response = await requestJson(`${values.apiBase}/api/ai/diagnose/${encodeURIComponent(taskNo)}/report/regenerate`, {
      method: 'POST'
    });
    markChecks(['report-regenerate', 'ai-report', 'report-schema']);
    renderReportResponse(response, '报告已重生成');
    return response;
  });
}

async function finishTask() {
  const taskNo = taskNoInput.value.trim();
  const values = getFormValues();
  if (!taskNo) {
    renderResponse({ message: '请先创建或填写 taskNo' }, '缺少 taskNo');
    return;
  }

  await runAction('标记完成', async () => {
    const response = await requestJson(`${values.apiBase}/api/diagnose/tasks/${encodeURIComponent(taskNo)}/finish`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        conclusion: '已完成第三阶段前端验收采集'
      })
    });
    renderResponse(response || { status: 'FINISHED' }, '已完成');
    await refreshDetail({ silent: true });
    return response || { status: 'FINISHED' };
  });
}

async function runAction(label, action) {
  if (state.loading) return null;
  setLoading(true, label);
  renderLoading();
  animateSignal();
  try {
    const response = await action();
    pushHistory(label, true);
    setServiceState('success', `${label}成功`);
    return response;
  } catch (error) {
    const failure = {
      code: error.code || 'REQUEST_ERROR',
      message: error.message
    };
    state.lastResponse = failure;
    pushHistory(label, false);
    renderResponse(failure, '请求失败');
    setServiceState('error', `${label}失败`);
    return failure;
  } finally {
    setLoading(false);
  }
}

async function requestJson(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  const payload = text ? safeJsonParse(text) : null;
  if (!response.ok) {
    const error = new Error(payload?.message || `HTTP ${response.status}`);
    error.code = payload?.code;
    throw error;
  }
  return payload || {};
}

function safeJsonParse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

function connectDiagnoseStream(apiBase, taskNo) {
  if (!taskNo) return;
  const streamUrl = `${apiBase}/api/diagnose/tasks/${encodeURIComponent(taskNo)}/stream`;
  const eventSource = new EventSource(streamUrl);
  state.eventSource = eventSource;
  disconnectStreamButton.disabled = false;
  markChecks(['sse-stream']);
  setServiceState('running', 'SSE 已连接');

  STREAM_EVENT_TYPES.forEach((eventType) => {
    eventSource.addEventListener(eventType, (event) => {
      handleDiagnoseEvent(eventType, event);
    });
  });

  eventSource.onerror = () => {
    if (state.eventSource !== eventSource) {
      return;
    }
    pushStreamEvent({
      eventType: 'STREAM_ERROR',
      message: 'SSE 连接异常或已关闭',
      time: new Date().toISOString()
    });
    closeEventStream(false);
  };
}

function handleDiagnoseEvent(eventType, event) {
  const payload = safeJsonParse(event.data || '{}');
  pushStreamEvent({
    ...payload,
    eventType: payload.eventType || eventType
  });
  markStreamChecks(eventType);

  if (eventType === 'TASK_FINISHED') {
    closeEventStream(false);
    setServiceState('success', '诊断完成');
    refreshDetail({ silent: true });
  }

  if (eventType === 'TASK_FAILED') {
    closeEventStream(false);
    setServiceState('error', '诊断失败');
    refreshDetail({ silent: true });
  }
}

function closeEventStream(recordHistory) {
  if (!state.eventSource) return;
  state.eventSource.close();
  state.eventSource = null;
  disconnectStreamButton.disabled = true;
  if (recordHistory) {
    pushHistory('断开 SSE', true);
    setServiceState('', '等待验收');
  }
}

function resetStreamEvents() {
  state.sseEvents = [];
  renderStreamEvents();
}

function pushStreamEvent(event) {
  state.sseEvents = [
    {
      eventType: event.eventType || 'MESSAGE',
      message: event.message || '',
      command: event.command || '',
      toolName: event.toolName || '',
      success: event.success,
      time: event.time || new Date().toISOString()
    },
    ...state.sseEvents
  ].slice(0, 24);
  renderStreamEvents();
}

function markStreamChecks(eventType) {
  const ids = [];
  if (eventType === 'TASK_CREATED') {
    ids.push('sse-task-created');
  }
  if (eventType === 'INTENT_CLASSIFYING' || eventType === 'INTENT_CLASSIFIED') {
    ids.push('sse-intent');
  }
  if (eventType === 'TOOL_CALL_START') {
    ids.push('sse-tool-start');
  }
  if (eventType === 'TOOL_CALL_SUCCESS' || eventType === 'TOOL_CALL_FAILED') {
    ids.push('sse-tool-result');
  }
  if (eventType === 'AI_ANALYZING' || eventType === 'REPORT_GENERATED') {
    ids.push('sse-report', 'ai-report');
  }
  if (eventType === 'TASK_FINISHED' || eventType === 'TASK_FAILED') {
    ids.push('sse-finished');
  }
  if (ids.length) {
    markChecks(ids);
  }
}

function getFormValues() {
  const formData = new FormData(taskForm);
  const values = {
    apiBase: normalizeApiBase(formData.get('apiBase')),
    appId: String(formData.get('appId') || '').trim(),
    env: String(formData.get('env') || '').trim(),
    userId: String(formData.get('userId') || '').trim(),
    question: String(formData.get('question') || '').trim(),
    diagnoseType: String(formData.get('diagnoseType') || 'UNKNOWN').trim(),
    mode: String(formData.get('mode') || 'TOOL_CALLING').trim(),
    targetUri: String(formData.get('targetUri') || '').trim(),
    targetClass: String(formData.get('targetClass') || '').trim(),
    targetMethod: String(formData.get('targetMethod') || '').trim()
  };
  localStorage.setItem('diagnosis.apiBase', values.apiBase);
  return values;
}

function normalizeApiBase(value) {
  const text = String(value || '').trim() || 'http://localhost:9001';
  return text.replace(/\/+$/, '');
}

function setActiveTaskNo(taskNo, persist) {
  state.activeTaskNo = taskNo || '';
  taskNoInput.value = state.activeTaskNo;
  document.querySelector('[data-active-task-pill]').textContent = state.activeTaskNo || '未创建';
  copyTaskButton.disabled = !state.activeTaskNo;
  if (persist) {
    localStorage.setItem('diagnosis.activeTaskNo', state.activeTaskNo);
  }
}

function setCommand(commandType) {
  state.commandType = commandType;
  commandButtons.forEach((button) => {
    const active = button.dataset.command === commandType;
    button.classList.toggle('is-active', active);
    button.setAttribute('aria-pressed', String(active));
  });
}

function setLoading(loading, label = '') {
  state.loading = loading;
  [
    agentDiagnoseButton,
    aiDiagnoseButton,
    createButton,
    runButton,
    refreshButton,
    reportButton,
    regenerateReportButton,
    finishButton,
    executeTaskButton,
    executeFreeButton,
    missingTaskButton
  ].forEach((button) => {
    button.disabled = loading;
  });
  setServiceState(loading ? 'running' : '', loading ? `${label}中` : '等待验收');
}

function setServiceState(status, text) {
  document.querySelector('[data-service-state]').textContent = text;
  document.querySelector('[data-state-dot]').dataset.status = status;
}

function renderLoading() {
  document.querySelector('[data-result-body]').innerHTML = `
    <div class="loading-state" aria-live="polite">
      <span></span>
      <span></span>
      <span></span>
    </div>
  `;
}

function renderResponse(response, title) {
  state.lastResponse = response;
  const failed = Boolean(response?.code || response?.errorMessage || response?.message);
  document.querySelector('[data-result-body]').innerHTML = `
    <div class="response-summary ${failed ? 'is-error' : 'is-success'}">
      <span>${escapeHtml(title)}</span>
      <strong>${escapeHtml(response?.taskNo || response?.requestNo || response?.code || response?.status || 'OK')}</strong>
    </div>
    <pre class="result-output">${escapeHtml(JSON.stringify(response, null, 2))}</pre>
  `;
  copyButton.disabled = false;
  revealResult();
}

function renderReportResponse(response, title) {
  state.lastResponse = response;
  const markdown = response?.reportMarkdown || '';
  state.reportMarkdown = markdown;
  const failed = Boolean(response?.code || response?.errorMessage || response?.message || response?.status === 'FAILED');
  document.querySelector('[data-result-body]').innerHTML = `
    <div class="response-summary ${failed ? 'is-error' : 'is-success'}">
      <span>${escapeHtml(title)}</span>
      <strong>${escapeHtml(response?.taskNo || response?.code || response?.status || 'OK')}</strong>
    </div>
    ${markdown ? `<article class="report-preview">${renderMarkdownPreview(markdown)}</article>` : ''}
    <pre class="result-output ${markdown ? 'compact-output' : ''}">${escapeHtml(JSON.stringify(response, null, 2))}</pre>
  `;
  copyButton.disabled = false;
  revealResult();
}

function renderTaskDetail(detail) {
  const task = detail?.task || {};
  const records = detail?.commandRecords || [];
  document.querySelector('[data-detail-body]').innerHTML = `
    <div class="task-card">
      <div class="task-status-row">
        <span class="status-pill status-${String(task.status || '').toLowerCase()}">${escapeHtml(task.status || 'UNKNOWN')}</span>
        <strong>${escapeHtml(task.taskNo || '-')}</strong>
      </div>
      <dl class="task-meta">
        <div><dt>App</dt><dd>${escapeHtml(task.appId || '-')}</dd></div>
        <div><dt>Env</dt><dd>${escapeHtml(task.env || '-')}</dd></div>
        <div><dt>Type</dt><dd>${escapeHtml(task.diagnoseType || '-')}</dd></div>
        <div><dt>User</dt><dd>${escapeHtml(task.userId || '-')}</dd></div>
      </dl>
      ${renderTargetInfo(task)}
      <p class="task-question">${escapeHtml(task.question || '无问题描述')}</p>
      ${task.conclusion ? `<p class="task-conclusion">${escapeHtml(task.conclusion)}</p>` : ''}
      ${task.errorMessage ? `<p class="task-error">${escapeHtml(task.errorMessage)}</p>` : ''}
    </div>
    <div class="record-list">
      <div class="record-head">
        <strong>命令链路</strong>
        <span>${records.length} 条</span>
      </div>
      ${records.length ? records.map(renderRecord).join('') : '<p class="subtle">当前任务还没有命令记录。</p>'}
    </div>
  `;
}

function renderTargetInfo(task) {
  const items = [
    ['URI', task.targetUri],
    ['Class', task.targetClass],
    ['Method', task.targetMethod]
  ].filter(([, value]) => value);

  if (!items.length) return '';

  return `
    <dl class="target-meta">
      ${items.map(([label, value]) => `<div><dt>${label}</dt><dd>${escapeHtml(value)}</dd></div>`).join('')}
    </dl>
  `;
}

function renderRecord(record, index) {
  return `
    <article class="record-item">
      <span class="record-index">${index + 1}</span>
      <div>
        <strong>${escapeHtml(record.command || record.commandType || '-')}</strong>
        <span>${escapeHtml(record.requestNo || '-')}</span>
      </div>
      <small class="${record.success ? 'ok' : 'fail'}">${record.success ? '成功' : '失败'} / ${record.costMillis ?? 0} ms</small>
    </article>
  `;
}

function renderEmptyDetail() {
  return `
    <div class="empty-state compact-empty">
      <span class="empty-line"></span>
      <p>创建任务后可查看状态和命令链路。</p>
    </div>
  `;
}

function markChecks(ids) {
  ids.forEach((id) => state.checks.add(id));
  renderChecklist();
}

function markRunChecks(diagnoseType, response) {
  const ids = ['run-api', 'schema-record'];
  const commands = (response.commandResults || []).map((item) => item.command);

  if (response.status === 'FINISHED') {
    ids.push('status-finished', 'record-task-no', 'detail-records');
  }
  if (response.status === 'FAILED') {
    ids.push('status-failed');
  }

  if (diagnoseType === 'HIGH_CPU' && commands.includes('dashboard -n 1') && commands.includes('thread -n 5')) {
    ids.push('plan-high-cpu');
  }
  if (diagnoseType === 'MEMORY_ABNORMAL'
    && commands.includes('memory')
    && commands.includes('dashboard -n 1')
    && commands.includes('jvm')) {
    ids.push('plan-memory');
  }
  if (diagnoseType === 'THREAD_BLOCKED'
    && commands.includes('dashboard -n 1')
    && commands.includes('thread')
    && commands.includes('thread -b')) {
    ids.push('plan-thread');
  }
  if (diagnoseType === 'SLOW_REQUEST' && commands.some((command) => command.startsWith('trace ') && command.endsWith(' -n 3'))) {
    ids.push('plan-slow');
  }

  markChecks(ids);
}

function markAiChecks(response) {
  const ids = ['ai-api'];
  if (response.taskNo) {
    ids.push('ai-create-task', 'schema-task');
  }
  if (response.diagnoseType && response.diagnoseType !== 'UNKNOWN') {
    ids.push('ai-intent');
  }
  if (response.status === 'FINISHED') {
    ids.push('ai-run-rule', 'status-finished', 'schema-record');
  }
  if (response.status === 'FAILED') {
    ids.push('status-failed');
  }
  if (response.reportMarkdown) {
    ids.push('ai-report', 'report-schema');
  }
  markChecks(ids);
}

function renderChecklist() {
  const list = document.querySelector('[data-checklist]');
  if (!list) return;
  list.innerHTML = ACCEPTANCE_ITEMS.map((item) => {
    const checked = state.checks.has(item.id);
    return `
      <label class="check-item ${checked ? 'is-done' : ''}">
        <input type="checkbox" ${checked ? 'checked' : ''} data-check="${item.id}" ${item.manual ? '' : 'disabled'} />
        <span>${escapeHtml(item.label)}</span>
        ${item.manual ? '<small>人工确认</small>' : ''}
      </label>
    `;
  }).join('');

  list.querySelectorAll('[data-check]').forEach((input) => {
    input.addEventListener('change', () => {
      if (input.checked) {
        state.checks.add(input.dataset.check);
      } else {
        state.checks.delete(input.dataset.check);
      }
      renderChecklist();
    });
  });

  document.querySelector('[data-check-count]').textContent = `${state.checks.size}/${ACCEPTANCE_ITEMS.length}`;
}

function pushHistory(label, success) {
  state.history = [
    {
      label,
      success,
      at: new Date().toLocaleTimeString('zh-CN', { hour12: false })
    },
    ...state.history
  ].slice(0, 6);
  renderHistory();
}

function renderHistory() {
  const list = document.querySelector('[data-history-list]');
  list.innerHTML = state.history.map((item) => `
    <article class="history-item">
      <div>
        <strong>${escapeHtml(item.label)}</strong>
        <span>${escapeHtml(item.at)}</span>
      </div>
      <small class="${item.success ? 'ok' : 'fail'}">${item.success ? '成功' : '失败'}</small>
    </article>
  `).join('');
}

function renderStreamEvents() {
  const list = document.querySelector('[data-stream-list]');
  const count = document.querySelector('[data-stream-count]');
  if (!list || !count) return;
  count.textContent = `${state.sseEvents.length} 条`;
  if (!state.sseEvents.length) {
    list.innerHTML = '<p class="subtle">启动 Agent 实时诊断后，这里会显示 SSE 事件。</p>';
    return;
  }
  list.innerHTML = state.sseEvents.map((event) => {
    const stateClass = event.success === false
      ? 'fail'
      : event.success === true
        ? 'ok'
        : '';
    return `
      <article class="stream-item">
        <div>
          <strong>${escapeHtml(event.eventType)}</strong>
          <span>${escapeHtml(formatEventTime(event.time))}</span>
        </div>
        <p>${escapeHtml(event.message || event.command || '-')}</p>
        ${event.toolName || event.command ? `
          <small class="${stateClass}">
            ${escapeHtml([event.toolName, event.command].filter(Boolean).join(' / '))}
          </small>
        ` : ''}
      </article>
    `;
  }).join('');
}

function formatEventTime(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value).replace('T', ' ').slice(0, 19);
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false });
}

function runIntroAnimation() {
  if (reduceMotion) return;
  anime({
    targets: '.reveal',
    translateY: [12, 0],
    opacity: [0, 1],
    delay: anime.stagger(70),
    duration: 520,
    easing: 'easeOutCubic'
  });
}

function animateSignal() {
  if (reduceMotion) return;
  anime.remove('.command-button.is-active');
  anime({
    targets: '.command-button.is-active',
    scale: [0.98, 1],
    duration: 240,
    easing: 'easeOutCubic'
  });
}

function animateButton(button) {
  if (reduceMotion) return;
  anime({
    targets: button,
    scale: [0.98, 1],
    duration: 220,
    easing: 'easeOutCubic'
  });
}

function revealResult() {
  if (reduceMotion) return;
  anime({
    targets: ['.response-summary', '.report-preview', '.result-output'],
    translateY: [8, 0],
    opacity: [0, 1],
    delay: anime.stagger(60),
    duration: 360,
    easing: 'easeOutCubic'
  });
}

function flashButton(button, text, resetText) {
  button.textContent = text;
  setTimeout(() => {
    button.textContent = resetText;
  }, 1200);
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderMarkdownPreview(markdown) {
  const lines = markdown.split('\n');
  return lines.map((line) => {
    if (line.startsWith('# ')) {
      return `<h3>${escapeHtml(line.slice(2))}</h3>`;
    }
    if (line.startsWith('## ')) {
      return `<h4>${escapeHtml(line.slice(3))}</h4>`;
    }
    if (line.startsWith('- ')) {
      return `<p class="report-line">- ${escapeHtml(line.slice(2))}</p>`;
    }
    if (!line.trim()) {
      return '<span class="report-space"></span>';
    }
    return `<p>${escapeHtml(line)}</p>`;
  }).join('');
}

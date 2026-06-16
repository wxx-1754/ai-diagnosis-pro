import anime from 'animejs/lib/anime.es.js';
import './styles.css';

const COMMANDS = [
  { type: 'dashboard', label: 'Dashboard', hint: '概览一次采样' },
  { type: 'thread', label: 'Thread', hint: '线程列表' },
  { type: 'topThread', label: 'Top thread', hint: 'CPU 前 5' },
  { type: 'jvm', label: 'JVM', hint: '运行时信息' },
  { type: 'memory', label: 'Memory', hint: '内存分布' }
];

const state = {
  commandType: 'jvm',
  loading: false,
  lastResponse: null,
  history: []
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
        <span data-service-state>等待连接</span>
      </div>
    </header>

    <section class="workspace">
      <section class="control-panel reveal" aria-labelledby="control-title">
        <div class="panel-heading">
          <p class="panel-kicker">Arthas gateway</p>
          <h1 id="control-title">受控诊断入口</h1>
          <p>选择命令类型，前端不会传入任意 Arthas 命令。</p>
        </div>

        <form id="diagnosis-form" class="diagnosis-form">
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
            <button class="primary-button" type="submit" data-submit-button>执行诊断</button>
            <button class="secondary-button" type="button" data-health-button>健康检查</button>
          </div>
        </form>
      </section>

      <section class="visual-panel reveal" aria-label="诊断链路状态">
        <div class="signal-map" data-signal-map>
          <div class="signal-node node-client">Frontend</div>
          <div class="signal-line line-a"></div>
          <div class="signal-node node-backend">Backend</div>
          <div class="signal-line line-b"></div>
          <div class="signal-node node-arthas">Arthas</div>
          <div class="signal-pulse" data-signal-pulse></div>
        </div>
        <div class="metric-strip">
          <div>
            <span>Command</span>
            <strong data-current-command>jvm</strong>
          </div>
          <div>
            <span>Cost</span>
            <strong data-cost>0 ms</strong>
          </div>
          <div>
            <span>State</span>
            <strong data-state-text>Idle</strong>
          </div>
        </div>
      </section>

      <section class="result-panel reveal" aria-labelledby="result-title">
        <div class="result-head">
          <div>
            <p class="panel-kicker">Response</p>
            <h2 id="result-title">诊断结果</h2>
          </div>
          <button class="text-button" type="button" data-copy-button disabled>复制 JSON</button>
        </div>
        <div class="result-body" data-result-body>
          <div class="empty-state">
            <span class="empty-line"></span>
            <p>执行一个命令后，返回内容会显示在这里。</p>
          </div>
        </div>
      </section>

      <aside class="history-panel reveal" aria-labelledby="history-title">
        <h2 id="history-title">最近请求</h2>
        <div data-history-list class="history-list">
          <p class="subtle">暂无记录</p>
        </div>
      </aside>
    </section>
  </main>
`;

const form = document.querySelector('#diagnosis-form');
const commandButtons = Array.from(document.querySelectorAll('[data-command]'));
const submitButton = document.querySelector('[data-submit-button]');
const healthButton = document.querySelector('[data-health-button]');
const copyButton = document.querySelector('[data-copy-button]');

commandButtons.forEach((button) => {
  button.addEventListener('click', () => {
    setCommand(button.dataset.command);
    animateButton(button);
  });
});

form.addEventListener('submit', (event) => {
  event.preventDefault();
  executeCommand(state.commandType);
});

healthButton.addEventListener('click', () => {
  setCommand('jvm');
  executeCommand('jvm', true);
});

copyButton.addEventListener('click', async () => {
  if (!state.lastResponse) return;
  await navigator.clipboard.writeText(JSON.stringify(state.lastResponse, null, 2));
  copyButton.textContent = '已复制';
  setTimeout(() => {
    copyButton.textContent = '复制 JSON';
  }, 1200);
});

runIntroAnimation();

function setCommand(commandType) {
  state.commandType = commandType;
  document.querySelector('[data-current-command]').textContent = commandType;
  commandButtons.forEach((button) => {
    const active = button.dataset.command === commandType;
    button.classList.toggle('is-active', active);
    button.setAttribute('aria-pressed', String(active));
  });
}

async function executeCommand(commandType, healthCheck = false) {
  if (state.loading) return;
  const values = getFormValues();
  localStorage.setItem('diagnosis.apiBase', values.apiBase);
  setLoading(true);
  renderLoading();
  animateSignal();

  try {
    const response = healthCheck
      ? await requestHealth(values)
      : await requestExecute(values, commandType);
    state.lastResponse = response;
    pushHistory(response, commandType);
    renderResponse(response);
    updateMetrics(response);
  } catch (error) {
    const failure = {
      requestNo: 'LOCAL',
      appId: values.appId,
      env: values.env,
      command: commandType,
      success: false,
      errorMessage: error.message,
      costMillis: 0
    };
    state.lastResponse = failure;
    pushHistory(failure, commandType);
    renderResponse(failure);
    updateMetrics(failure);
  } finally {
    setLoading(false);
  }
}

async function requestExecute(values, commandType) {
  const response = await fetch(`${values.apiBase}/api/arthas/execute`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      appId: values.appId,
      env: values.env,
      commandType
    })
  });
  return parseJsonResponse(response);
}

async function requestHealth(values) {
  const params = new URLSearchParams({ appId: values.appId, env: values.env });
  const response = await fetch(`${values.apiBase}/api/arthas/health?${params.toString()}`);
  return parseJsonResponse(response);
}

async function parseJsonResponse(response) {
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(payload?.message || `HTTP ${response.status}`);
  }
  return payload;
}

function getFormValues() {
  const formData = new FormData(form);
  return {
    apiBase: normalizeApiBase(formData.get('apiBase')),
    appId: String(formData.get('appId') || '').trim(),
    env: String(formData.get('env') || '').trim()
  };
}

function normalizeApiBase(value) {
  const text = String(value || '').trim() || 'http://localhost:9001';
  return text.replace(/\/+$/, '');
}

function setLoading(loading) {
  state.loading = loading;
  submitButton.disabled = loading;
  healthButton.disabled = loading;
  submitButton.textContent = loading ? '执行中' : '执行诊断';
  document.querySelector('[data-state-text]').textContent = loading ? 'Running' : 'Idle';
  document.querySelector('[data-service-state]').textContent = loading ? '请求中' : '等待连接';
  document.querySelector('[data-state-dot]').dataset.status = loading ? 'running' : '';
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

function renderResponse(response) {
  const success = Boolean(response.success);
  const output = response.output || response.errorMessage || '没有返回内容';
  document.querySelector('[data-result-body]').innerHTML = `
    <div class="response-summary ${success ? 'is-success' : 'is-error'}">
      <span>${success ? '成功' : '失败'}</span>
      <strong>${escapeHtml(response.command || response.code || 'unknown')}</strong>
    </div>
    <pre class="result-output">${escapeHtml(formatOutput(output))}</pre>
  `;
  copyButton.disabled = false;
  document.querySelector('[data-service-state]').textContent = success ? '执行成功' : '执行失败';
  document.querySelector('[data-state-dot]').dataset.status = success ? 'success' : 'error';

  if (!reduceMotion) {
    anime({
      targets: ['.response-summary', '.result-output'],
      translateY: [8, 0],
      opacity: [0, 1],
      delay: anime.stagger(80),
      duration: 420,
      easing: 'easeOutCubic'
    });
  }
}

function formatOutput(output) {
  if (typeof output !== 'string') {
    return JSON.stringify(output, null, 2);
  }
  try {
    return JSON.stringify(JSON.parse(output), null, 2);
  } catch {
    return output;
  }
}

function updateMetrics(response) {
  document.querySelector('[data-cost]').textContent = `${response.costMillis ?? 0} ms`;
  document.querySelector('[data-state-text]').textContent = response.success ? 'Success' : 'Failed';
}

function pushHistory(response, commandType) {
  state.history = [
    {
      requestNo: response.requestNo || 'LOCAL',
      command: response.command || commandType,
      success: Boolean(response.success),
      costMillis: response.costMillis ?? 0
    },
    ...state.history
  ].slice(0, 5);
  renderHistory();
}

function renderHistory() {
  const list = document.querySelector('[data-history-list]');
  list.innerHTML = state.history.map((item) => `
    <article class="history-item">
      <div>
        <strong>${escapeHtml(item.command)}</strong>
        <span>${escapeHtml(item.requestNo)}</span>
      </div>
      <small class="${item.success ? 'ok' : 'fail'}">${item.success ? '成功' : '失败'} / ${item.costMillis} ms</small>
    </article>
  `).join('');
}

function runIntroAnimation() {
  if (reduceMotion) return;
  anime({
    targets: '.reveal',
    translateY: [16, 0],
    opacity: [0, 1],
    delay: anime.stagger(90),
    duration: 680,
    easing: 'easeOutCubic'
  });
  anime({
    targets: '.signal-node',
    scale: [0.96, 1],
    opacity: [0, 1],
    delay: anime.stagger(120, { start: 300 }),
    duration: 520,
    easing: 'easeOutCubic'
  });
}

function animateSignal() {
  if (reduceMotion) return;
  anime.remove('[data-signal-pulse]');
  anime({
    targets: '[data-signal-pulse]',
    translateX: [0, 308],
    opacity: [0, 1, 1, 0],
    scale: [0.8, 1, 1, 0.8],
    duration: 980,
    easing: 'easeInOutCubic'
  });
}

function animateButton(button) {
  if (reduceMotion) return;
  anime({
    targets: button,
    scale: [0.98, 1],
    duration: 260,
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

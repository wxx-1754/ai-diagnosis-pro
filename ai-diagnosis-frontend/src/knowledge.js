// 知识库管理页：管理员令牌、文本/文件上传、检索预览、文档列表（删除/重建索引/查看分片）。
// 视觉与主诊断页同源：深色 token、单一 blue 强调色、8px 形状锁。
import { railHtml } from './rail.js';
import './knowledge.css';

const TOKEN_KEY = 'ai-diagnosis.kbAdminToken';

const SOURCE_TYPE_LABELS = {
  MANUAL: '手工知识',
  HISTORY_REPORT: '历史报告',
  RUNTIME: '运行时片段'
};

const STATUS_META = {
  INDEXED: { label: '已索引', tone: 'green' },
  INDEXING: { label: '索引中', tone: 'amber' },
  PENDING: { label: '待处理', tone: 'neutral' },
  FAILED: { label: '失败', tone: 'red' },
  DELETED: { label: '已删除', tone: 'neutral' }
};

const DIAGNOSE_TYPES = [
  { value: '', label: '不限（全局）' },
  { value: 'HIGH_CPU', label: '高 CPU' },
  { value: 'SLOW_REQUEST', label: '接口慢' },
  { value: 'THREAD_BLOCKED', label: '线程阻塞' },
  { value: 'MEMORY_ABNORMAL', label: '内存异常' }
];

// 分类预设值（后端 category 为自由字符串，无字典接口；给常用值快捷入口 + 自定义）。
const CATEGORIES = [
  { value: '', label: '不指定' },
  { value: 'SOP', label: 'SOP 排障手册' },
  { value: 'MANUAL', label: '组件手册' },
  { value: 'CASE', label: '案例' },
  { value: 'JVM', label: 'JVM' },
  { value: 'SQL', label: 'SQL' },
  { value: 'COMPONENT', label: '自研组件' },
  { value: '__custom__', label: '自定义...' }
];

const ENV_LABELS = {
  dev: '开发', test: '测试', uat: '验收', staging: '预发', prod: '生产'
};

const PAGE_SIZE = 20;

export function mountKnowledge(container, { apiBase, query = '' }) {
  const params = parseQuery(query);
  const local = {
    apiBase,
    token: localStorage.getItem(TOKEN_KEY) || '',
    filters: {
      sourceType: params.sourceType || '',
      status: params.status || ''
    },
    page: 0,
    list: [],
    loadingList: false,
    listError: '',
    toastTimer: null,
    instanceOptions: []
  };
  container.innerHTML = knowledgeShell(local);
  bindKnowledge(container, local);
  loadInstanceOptions(local);
  if (local.token) {
    loadList(local);
  } else {
    renderAwaitToken(local);
  }
  runReveal(container);
}

function knowledgeShell(local) {
  return `
    <div class="diagnosis-studio">
      ${railHtml('knowledge')}
      <div class="workspace knowledge-workspace">
        <header class="topbar knowledge-topbar reveal">
          <div class="title-block">
            <span>AI + Arthas</span>
            <h1>知识库</h1>
            <p>RAG 参考知识管理</p>
          </div>
          <div class="knowledge-toolbar">
            <label class="token-field">
              <span>管理令牌</span>
              <input type="password" id="kbToken" value="${escapeHtml(local.token)}" placeholder="X-Diagnosis-Admin-Token" autocomplete="off" />
            </label>
            <button type="button" class="secondary-button" data-refresh>
              <i class="ph ph-arrow-clockwise" aria-hidden="true"></i>刷新
            </button>
          </div>
        </header>

        <div class="kb-grid">
          <div class="kb-col">
            ${uploadPanel(local)}
            ${searchPanel(local)}
          </div>
          <div class="kb-col">
            ${listPanel(local)}
          </div>
        </div>

        <div class="kb-toast" data-toast hidden></div>
      </div>
    </div>
  `;
}

function uploadPanel() {
  return `
    <section class="kb-panel reveal" aria-label="上传知识">
      <div class="kb-panel-head">
        <p class="panel-label">上传知识</p>
        <div class="kb-tabs" role="tablist">
          <button type="button" class="kb-tab is-active" data-tab="text" role="tab">文本</button>
          <button type="button" class="kb-tab" data-tab="file" role="tab">文件</button>
        </div>
      </div>

      <form class="kb-form" data-form="text">
        <label class="kb-field kb-field-wide">
          <span>标题</span>
          <input data-text="title" placeholder="如：Arthas trace 排查接口慢 SOP" />
        </label>
        <label class="kb-field kb-field-wide">
          <span>正文</span>
          <textarea data-text="content" rows="6" placeholder="粘贴 Markdown 或纯文本知识内容"></textarea>
        </label>
        <div class="kb-field-row">
          <label class="kb-field">
            <span>分类</span>
            <select data-text="categorySelect">${CATEGORIES.map((c) => `<option value="${c.value}">${c.label}</option>`).join('')}</select>
          </label>
          <label class="kb-field kb-field-custom" hidden>
            <span>自定义分类</span>
            <input data-text="category" placeholder="输入自定义分类" />
          </label>
          <label class="kb-field">
            <span>诊断类型</span>
            <select data-text="diagnoseType">${DIAGNOSE_TYPES.map((t) => `<option value="${t.value}">${t.label}</option>`).join('')}</select>
          </label>
          <label class="kb-field">
            <span>应用</span>
            <select data-text="appId" data-scope="appId">
              <option value="">全局（不限）</option>
            </select>
          </label>
          <label class="kb-field">
            <span>环境</span>
            <select data-text="env" data-scope="env">
              <option value="">全局（不限）</option>
            </select>
          </label>
        </div>
        <div class="kb-form-actions">
          <button type="submit" class="primary-button">提交文本</button>
          <span class="kb-hint">入库前自动脱敏，同内容按 hash 去重</span>
        </div>
      </form>

      <form class="kb-form" data-form="file" hidden>
        <label class="kb-field kb-field-wide">
          <span>文件（.md / .markdown / .txt）</span>
          <input type="file" data-file="file" accept=".md,.markdown,.txt" />
        </label>
        <div class="kb-field-row">
          <label class="kb-field"><span>标题</span><input data-file="title" placeholder="留空用文件名" /></label>
          <label class="kb-field">
            <span>分类</span>
            <select data-file="categorySelect">${CATEGORIES.map((c) => `<option value="${c.value}">${c.label}</option>`).join('')}</select>
          </label>
          <label class="kb-field kb-field-custom" hidden>
            <span>自定义分类</span>
            <input data-file="category" placeholder="输入自定义分类" />
          </label>
          <label class="kb-field">
            <span>诊断类型</span>
            <select data-file="diagnoseType">${DIAGNOSE_TYPES.map((t) => `<option value="${t.value}">${t.label}</option>`).join('')}</select>
          </label>
          <label class="kb-field">
            <span>应用</span>
            <select data-file="appId" data-scope="appId">
              <option value="">全局（不限）</option>
            </select>
          </label>
          <label class="kb-field">
            <span>环境</span>
            <select data-file="env" data-scope="env">
              <option value="">全局（不限）</option>
            </select>
          </label>
        </div>
        <div class="kb-form-actions">
          <button type="submit" class="primary-button">上传文件</button>
        </div>
      </form>
    </section>
  `;
}

function searchPanel() {
  return `
    <section class="kb-panel reveal" aria-label="检索预览">
      <div class="kb-panel-head">
        <p class="panel-label">检索预览</p>
        <span class="kb-hint">验证某 query 能召回哪些知识</span>
      </div>
      <form class="kb-form" data-form="search">
        <label class="kb-field kb-field-wide">
          <span>问题 / 关键词</span>
          <input data-search="question" placeholder="如：订单接口慢 trace 到 BaseExecutor" />
        </label>
        <div class="kb-field-row">
          <label class="kb-field">
            <span>诊断类型</span>
            <select data-search="diagnoseType">${DIAGNOSE_TYPES.map((t) => `<option value="${t.value}">${t.label}</option>`).join('')}</select>
          </label>
          <label class="kb-field"><span>应用</span><input data-search="appId" placeholder="可选" /></label>
          <label class="kb-field"><span>TopK</span><input data-search="topK" type="number" min="1" max="20" value="5" /></label>
        </div>
        <div class="kb-form-actions">
          <button type="submit" class="secondary-button">检索</button>
        </div>
      </form>
      <div class="kb-search-results" data-search-results></div>
    </section>
  `;
}

function listPanel() {
  return `
    <section class="kb-panel reveal" aria-label="文档列表">
      <div class="kb-panel-head">
        <p class="panel-label">文档列表</p>
        <div class="kb-filters">
          <select data-filter="sourceType">
            <option value="">全部来源</option>
            ${Object.entries(SOURCE_TYPE_LABELS).map(([value, label]) => `<option value="${value}">${label}</option>`).join('')}
          </select>
          <select data-filter="status">
            <option value="">全部状态</option>
            ${Object.entries(STATUS_META).map(([value, meta]) => `<option value="${value}">${meta.label}</option>`).join('')}
          </select>
        </div>
      </div>
      <div class="kb-list" data-doc-list></div>
      <div class="kb-list-footer" data-list-footer hidden></div>
    </section>
  `;
}

function renderAwaitToken(local) {
  local.root.querySelector('[data-doc-list]').innerHTML = stateHtml(
    'ph-lock-key', '请先在右上角填写管理令牌', ''
  );
}

function renderDocList(local) {
  const root = local.root.querySelector('[data-doc-list]');
  const footer = local.root.querySelector('[data-list-footer]');

  if (local.listError) {
    root.innerHTML = stateHtml('ph-warning-circle', local.listError, 'is-error');
    footer.hidden = true;
    return;
  }
  if (!local.list.length) {
    root.innerHTML = stateHtml('ph-stack', '暂无知识文档', '');
    footer.hidden = true;
    return;
  }
  root.innerHTML = local.list.map(docCardHtml).join('');
  footer.hidden = false;
  footer.innerHTML = `
    <span>${local.list.length} 条 · 第 ${local.page + 1} 页</span>
    <button type="button" class="ghost-button" data-prev ${local.page === 0 ? 'disabled' : ''}>上一页</button>
    <button type="button" class="ghost-button" data-next ${local.list.length < PAGE_SIZE ? 'disabled' : ''}>下一页</button>
  `;
}

function docCardHtml(doc) {
  const status = STATUS_META[doc.status] || { label: doc.status, tone: 'neutral' };
  const scope = (value) => (value && value !== '__GLOBAL__' ? value : '全局');
  const meta = [
    ['来源', SOURCE_TYPE_LABELS[doc.sourceType] || doc.sourceType || '—', false],
    ['分类', doc.category || '—', !doc.category],
    ['分片', `${doc.chunkCount ?? 0}`, false],
    ['诊断类型', scope(doc.diagnoseType), !doc.diagnoseType || doc.diagnoseType === '__GLOBAL__'],
    ['应用', scope(doc.appId), !doc.appId || doc.appId === '__GLOBAL__'],
    ['更新', formatDateTime(doc.updatedAt), false]
  ];
  return `
    <article class="kb-doc" data-doc-no="${escapeHtml(doc.docNo)}">
      <div class="kb-doc-head">
        <div class="kb-doc-title">
          <strong>${escapeHtml(doc.title)}</strong>
          <small>${escapeHtml(doc.docNo)}</small>
        </div>
        <span class="kb-status" data-tone="${status.tone}">${status.label}</span>
      </div>
      <div class="kb-doc-meta">
        ${meta.map(([label, value, muted]) => `
          <div class="kb-meta-item">
            <span class="kb-meta-label">${label}</span>
            <span class="kb-meta-value${muted ? ' is-muted' : ''}">${escapeHtml(value)}</span>
          </div>
        `).join('')}
      </div>
      ${doc.errorMessage ? `<p class="kb-doc-error">${escapeHtml(doc.errorMessage)}</p>` : ''}
      <div class="kb-doc-actions">
        <button type="button" class="ghost-button" data-action="chunks">
          <i class="ph ph-list-dashes" aria-hidden="true"></i>查看分片
        </button>
        <button type="button" class="ghost-button" data-action="reindex">
          <i class="ph ph-arrows-clockwise" aria-hidden="true"></i>重建索引
        </button>
        <button type="button" class="ghost-button kb-danger" data-action="delete">
          <i class="ph ph-trash" aria-hidden="true"></i>删除
        </button>
      </div>
      <div class="kb-chunk-view" data-chunk-view hidden></div>
    </article>
  `;
}

function renderSearchResults(container, results) {
  if (!results || !results.length) {
    container.innerHTML = stateHtml('ph-magnifying-glass', '未检索到相关知识', '');
    return;
  }
  container.innerHTML = results.map((chunk) => `
    <article class="kb-search-item">
      <div class="kb-doc-head">
        <div class="kb-doc-title">
          <strong>${escapeHtml(chunk.title || chunk.docNo)}</strong>
          <small>${escapeHtml(chunk.citationCode || '')} · 相似度 ${formatScore(chunk.similarity)}</small>
        </div>
        <span class="kb-status" data-tone="green">${escapeHtml(chunk.sourceType || '')}</span>
      </div>
      <pre class="kb-chunk-content">${escapeHtml(chunk.content || '')}</pre>
    </article>
  `).join('');
}

// 读取分类：选预设值则用 select 的值，选「自定义...」则用文本输入框的值。
function readCategory(form) {
  const select = form.querySelector('[data-text="categorySelect"], [data-file="categorySelect"]');
  if (!select) return '';
  if (select.value === '__custom__') {
    const custom = form.querySelector('[data-text="category"], [data-file="category"]');
    return custom ? custom.value.trim() : '';
  }
  return select.value;
}

// 分类下拉切换：选「自定义...」时显示文本输入框。
function syncCategoryField(select) {
  const form = select.closest('form');
  const customField = form.querySelector('.kb-field-custom');
  customField.hidden = select.value !== '__custom__';
}

// 从 /api/app-instances/options 加载应用列表，填充两个 form 的 appId 下拉。
async function loadInstanceOptions(local) {
  try {
    const options = await fetch(`${local.apiBase}/api/app-instances/options`).then(handleResponse);
    local.instanceOptions = Array.isArray(options)
      ? options.filter((o) => o && o.appId && o.env)
      : [];
  } catch {
    local.instanceOptions = [];
  }
  local.root.querySelectorAll('[data-scope="appId"]').forEach((appIdSelect) => {
    populateAppOptions(local, appIdSelect);
    populateEnvOptions(local, appIdSelect);
  });
}

function populateAppOptions(local, appIdSelect) {
  const services = new Map();
  local.instanceOptions.forEach((option) => {
    if (!services.has(option.appId)) {
      services.set(option.appId, option.appName || option.appId);
    }
  });
  const current = appIdSelect.value;
  appIdSelect.innerHTML = '<option value="">全局（不限）</option>'
    + [...services.entries()].map(([appId, name]) => {
        const label = name && name !== appId ? `${name} (${appId})` : appId;
        return `<option value="${escapeHtml(appId)}">${escapeHtml(label)}</option>`;
      }).join('');
  appIdSelect.value = [...services.keys()].includes(current) ? current : '';
}

function populateEnvOptions(local, appIdSelect) {
  const form = appIdSelect.closest('form');
  const envSelect = form.querySelector('[data-scope="env"]');
  if (!envSelect) return;
  const envs = [...new Set(
    local.instanceOptions
      .filter((option) => option.appId === appIdSelect.value)
      .map((option) => option.env)
  )];
  const current = envSelect.value;
  envSelect.innerHTML = '<option value="">全局（不限）</option>'
    + envs.map((env) => `<option value="${escapeHtml(env)}">${escapeHtml(ENV_LABELS[env] || env)}</option>`).join('');
  envSelect.value = envs.includes(current) ? current : '';
}

function stateHtml(icon, text, extraClass) {
  return `
    <div class="kb-state ${extraClass}">
      <i class="ph ${icon}" aria-hidden="true"></i>
      <span>${escapeHtml(text)}</span>
    </div>
  `;
}

function skeletonHtml() {
  return Array.from({ length: 3 }).map(() => `
    <div class="kb-skeleton">
      <div class="kb-skeleton-line mid"></div>
      <div class="kb-skeleton-line short"></div>
      <div class="kb-skeleton-line"></div>
    </div>
  `).join('');
}

function bindKnowledge(container, local) {
  local.root = container;

  container.querySelector('#kbToken').addEventListener('change', (event) => {
    local.token = event.target.value.trim();
    localStorage.setItem(TOKEN_KEY, local.token);
    showToast(local, '令牌已保存');
    if (local.token) loadList(local);
    else renderAwaitToken(local);
  });

  container.querySelectorAll('.kb-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      container.querySelectorAll('.kb-tab').forEach((t) => t.classList.remove('is-active'));
      tab.classList.add('is-active');
      const target = tab.getAttribute('data-tab');
      container.querySelector('[data-form="text"]').hidden = target !== 'text';
      container.querySelector('[data-form="file"]').hidden = target !== 'file';
    });
  });

  container.querySelector('[data-form="text"]').addEventListener('submit', (event) => {
    event.preventDefault();
    submitText(local);
  });
  container.querySelector('[data-form="file"]').addEventListener('submit', (event) => {
    event.preventDefault();
    submitFile(local);
  });
  container.querySelector('[data-form="search"]').addEventListener('submit', (event) => {
    event.preventDefault();
    runSearch(local);
  });

  container.querySelector('[data-refresh]').addEventListener('click', () => loadList(local));

  // 分类下拉：选「自定义...」切换为文本输入。
  container.querySelectorAll('[data-text="categorySelect"], [data-file="categorySelect"]').forEach((select) => {
    select.addEventListener('change', () => syncCategoryField(select));
  });

  // 应用 -> 环境级联：两个 form 各自的 appId change 重填 env 选项。
  container.querySelectorAll('[data-scope="appId"]').forEach((appIdSelect) => {
    appIdSelect.addEventListener('change', () => populateEnvOptions(local, appIdSelect));
  });

  container.querySelectorAll('[data-filter]').forEach((select) => {
    select.addEventListener('change', (event) => {
      local.filters[event.target.dataset.filter] = event.target.value;
      local.page = 0;
      loadList(local);
    });
  });

  container.querySelector('[data-doc-list]').addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const card = button.closest('[data-doc-no]');
    const docNo = card.getAttribute('data-doc-no');
    const action = button.getAttribute('data-action');
    if (action === 'chunks') showChunks(local, docNo, card, button);
    if (action === 'reindex') reindexDoc(local, docNo);
    if (action === 'delete') deleteDoc(local, docNo);
  });

  container.querySelector('[data-list-footer]').addEventListener('click', (event) => {
    const button = event.target.closest('button');
    if (!button || button.disabled) return;
    if (button.hasAttribute('data-prev') && local.page > 0) {
      local.page -= 1;
      loadList(local);
    } else if (button.hasAttribute('data-next')) {
      local.page += 1;
      loadList(local);
    }
  });
}

async function submitText(local) {
  if (!requireToken(local)) return;
  const form = local.root.querySelector('[data-form="text"]');
  const body = {
    title: form.querySelector('[data-text="title"]').value.trim(),
    content: form.querySelector('[data-text="content"]').value,
    category: readCategory(form),
    diagnoseType: form.querySelector('[data-text="diagnoseType"]').value,
    appId: form.querySelector('[data-text="appId"]').value,
    env: form.querySelector('[data-text="env"]').value,
    uploadedBy: 'web'
  };
  if (!body.title || !body.content) {
    showToast(local, '标题和正文不能为空', true);
    return;
  }
  try {
    await requestJson(local, '/api/admin/kb/documents/text', { method: 'POST', body: JSON.stringify(body) });
    showToast(local, '文本知识已入库');
    form.querySelector('[data-text="content"]').value = '';
    loadList(local);
  } catch (error) {
    showToast(local, error.message, true);
  }
}

async function submitFile(local) {
  if (!requireToken(local)) return;
  const form = local.root.querySelector('[data-form="file"]');
  const fileInput = form.querySelector('[data-file="file"]');
  if (!fileInput.files.length) {
    showToast(local, '请选择文件', true);
    return;
  }
  const formData = new FormData();
  formData.append('file', fileInput.files[0]);
  const title = form.querySelector('[data-file="title"]').value.trim();
  if (title) formData.append('title', title);
  const category = readCategory(form);
  if (category) formData.append('category', category);
  const diagnoseType = form.querySelector('[data-file="diagnoseType"]').value;
  if (diagnoseType) formData.append('diagnoseType', diagnoseType);
  const appId = form.querySelector('[data-file="appId"]').value;
  if (appId) formData.append('appId', appId);
  const env = form.querySelector('[data-file="env"]').value;
  if (env) formData.append('env', env);
  try {
    const response = await fetch(`${local.apiBase}/api/admin/kb/documents/upload`, {
      method: 'POST',
      headers: { 'X-Diagnosis-Admin-Token': local.token },
      body: formData
    });
    await handleResponse(response);
    showToast(local, '文件已上传入库');
    fileInput.value = '';
    loadList(local);
  } catch (error) {
    showToast(local, error.message, true);
  }
}

async function runSearch(local) {
  if (!requireToken(local)) return;
  const form = local.root.querySelector('[data-form="search"]');
  const body = {
    question: form.querySelector('[data-search="question"]').value.trim(),
    diagnoseType: form.querySelector('[data-search="diagnoseType"]').value,
    appId: form.querySelector('[data-search="appId"]').value.trim(),
    topK: Number(form.querySelector('[data-search="topK"]').value) || 5
  };
  if (!body.question) {
    showToast(local, '请输入检索问题', true);
    return;
  }
  const resultsEl = local.root.querySelector('[data-search-results]');
  resultsEl.innerHTML = stateHtml('ph-spinner-gap', '检索中...', '');
  try {
    const results = await requestJson(local, '/api/admin/kb/search', { method: 'POST', body: JSON.stringify(body) });
    renderSearchResults(resultsEl, results);
  } catch (error) {
    resultsEl.innerHTML = stateHtml('ph-warning-circle', error.message, 'is-error');
  }
}

async function loadList(local) {
  if (!local.token) {
    renderAwaitToken(local);
    return;
  }
  local.loadingList = true;
  local.root.querySelector('[data-doc-list]').innerHTML = skeletonHtml();
  local.root.querySelector('[data-list-footer]').hidden = true;
  const params = new URLSearchParams();
  if (local.filters.sourceType) params.set('sourceType', local.filters.sourceType);
  if (local.filters.status) params.set('status', local.filters.status);
  params.set('page', local.page);
  params.set('size', PAGE_SIZE);
  try {
    local.list = await requestJson(local, `/api/admin/kb/documents?${params.toString()}`);
    local.listError = '';
  } catch (error) {
    local.list = [];
    local.listError = error.message;
  }
  local.loadingList = false;
  renderDocList(local);
}

// 分片就地展开/收起：在文档卡片内部展示，不污染检索结果区。
// 已展开则收起；未加载则加载并展开。
async function showChunks(local, docNo, card, button) {
  const view = card.querySelector('[data-chunk-view]');
  if (!view) return;

  // 已展开 -> 收起。
  if (!view.hidden && view.dataset.loaded === '1') {
    view.hidden = true;
    button.innerHTML = '<i class="ph ph-list-dashes" aria-hidden="true"></i>查看分片';
    return;
  }

  view.hidden = false;
  view.dataset.loaded = '0';
  view.innerHTML = stateHtml('ph-spinner-gap', '加载中...', '');
  button.innerHTML = '<i class="ph ph-caret-up" aria-hidden="true"></i>收起分片';
  try {
    const chunks = await requestJson(local, `/api/admin/kb/documents/${encodeURIComponent(docNo)}/chunks`);
    const html = chunks.length
      ? chunks.map((c) => `<pre class="kb-chunk-content">#${c.chunkIndex}\n${escapeHtml(c.content || '')}</pre>`).join('')
      : stateHtml('ph-stack', '无分片', '');
    view.innerHTML = `<div class="kb-chunk-view-head">分片预览 · ${chunks.length} 片</div>${html}`;
    view.dataset.loaded = '1';
  } catch (error) {
    view.innerHTML = stateHtml('ph-warning-circle', error.message, 'is-error');
    view.dataset.loaded = '0';
  }
}

async function reindexDoc(local, docNo) {
  if (!confirm(`确认重建 ${docNo} 的索引？将按当前分片参数重新切分并 embedding。`)) return;
  try {
    await requestJson(local, `/api/admin/kb/documents/${encodeURIComponent(docNo)}/reindex`, { method: 'POST' });
    showToast(local, '索引已重建');
    loadList(local);
  } catch (error) {
    showToast(local, error.message, true);
  }
}

async function deleteDoc(local, docNo) {
  if (!confirm(`确认删除 ${docNo}？将同步删除向量与分片。`)) return;
  try {
    await requestJson(local, `/api/admin/kb/documents/${encodeURIComponent(docNo)}`, { method: 'DELETE' });
    showToast(local, '已删除');
    loadList(local);
  } catch (error) {
    showToast(local, error.message, true);
  }
}

function requireToken(local) {
  if (!local.token) {
    showToast(local, '请先填写管理令牌', true);
    return false;
  }
  return true;
}

async function requestJson(local, path, options = {}) {
  const headers = { 'X-Diagnosis-Admin-Token': local.token, ...(options.headers || {}) };
  if (options.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  const response = await fetch(`${local.apiBase}${path}`, { ...options, headers });
  return handleResponse(response);
}

async function handleResponse(response) {
  const text = await response.text();
  const payload = text ? safeJsonParse(text) : {};
  if (!response.ok) {
    throw new Error(payload.message || `HTTP ${response.status}`);
  }
  return payload;
}

function showToast(local, message, isError = false) {
  const toast = local.root.querySelector('[data-toast]');
  toast.textContent = message;
  toast.classList.toggle('is-error', isError);
  toast.hidden = false;
  requestAnimationFrame(() => toast.classList.add('is-visible'));
  clearTimeout(local.toastTimer);
  local.toastTimer = setTimeout(() => {
    toast.classList.remove('is-visible');
    setTimeout(() => { toast.hidden = true; }, 220);
  }, 2800);
}

function runReveal(scope) {
  scope.querySelectorAll('.reveal').forEach((el, index) => {
    setTimeout(() => el.classList.add('is-visible'), 45 * index);
  });
}

function parseQuery(query) {
  const params = {};
  new URLSearchParams(query).forEach((value, key) => { params[key] = value; });
  return params;
}

function safeJsonParse(text) {
  try { return JSON.parse(text); } catch { return {}; }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function formatScore(value) {
  if (value == null) return '—';
  return (typeof value === 'number' ? value : Number(value)).toFixed(3);
}

function formatDateTime(value) {
  if (!value) return '—';
  return String(value).replace('T', ' ').split('.')[0];
}

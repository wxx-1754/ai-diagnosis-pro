// 实例与数据源管理页：单菜单项 + 双 Tab。
// Tab 1：App Instance（Arthas HTTP / Tunnel 接入）CRUD + 连通性测试，调 /api/admin/app-instances。
// Tab 2：SQL 数据源 CRUD + 连通性测试，调 /api/admin/sql/datasources（后端 API 已存在）。
// 两个 Tab 用不同的管理令牌（DIAGNOSIS_APP_INSTANCE_ADMIN_TOKEN / DIAGNOSIS_SQL_ADMIN_TOKEN），
// 令牌栏随 Tab 切换绑定各自存储键。
// 视觉与主诊断页同源：深色 token、单一 blue 强调色、8px 形状锁。
import { railHtml } from './rail.js';
import './admin.css';

const TOKEN_KEYS = {
  instance: 'ai-diagnosis.appInstanceAdminToken',
  datasource: 'ai-diagnosis.sqlAdminToken'
};

const ENV_LABELS = { dev: '开发', test: '测试', uat: '验收', staging: '预发', prod: '生产' };

const INSTANCE_STATUS = {
  ONLINE: { label: '在线', tone: 'green' },
  OFFLINE: { label: '停用', tone: 'neutral' }
};

const DATASOURCE_STATUS = {
  ENABLED: { label: '启用', tone: 'green' },
  DISABLED: { label: '停用', tone: 'neutral' }
};

const ACCESS_MODES = ['HTTP', 'TUNNEL'];

export function mountAdmin(container, { apiBase, query = '' }) {
  const params = parseQuery(query);
  const initialTab = params.tab === 'datasource' ? 'datasource' : 'instance';
  const local = {
    apiBase,
    tab: initialTab,
    tokens: {
      instance: localStorage.getItem(TOKEN_KEYS.instance) || '',
      datasource: localStorage.getItem(TOKEN_KEYS.datasource) || ''
    },
    instanceList: [],
    datasourceList: [],
    appOptions: [],
    loading: false,
    listError: '',
    toastTimer: null,
    root: container
  };
  container.innerHTML = adminShell(local);
  bindAdmin(container, local);
  syncTokenField(local);
  loadAppOptions(local);
  loadActive(local);
  runReveal(container);
}

function adminShell(local) {
  return `
    <div class="diagnosis-studio">
      ${railHtml('admin')}
      <div class="workspace admin-workspace">
        <header class="topbar admin-topbar reveal">
          <div class="title-block">
            <span>基础设施配置</span>
            <h1>实例与数据源</h1>
            <p>App Instance 接入与 SQL 数据源管理</p>
          </div>
          <div class="admin-toolbar">
            <label class="token-field">
              <span data-token-label>管理令牌</span>
              <input type="password" id="adminToken" value="${escapeHtml(local.tokens[local.tab])}" placeholder="X-Diagnosis-Admin-Token" autocomplete="off" />
            </label>
            <button type="button" class="secondary-button" data-refresh>
              <i class="ph ph-arrow-clockwise" aria-hidden="true"></i>刷新
            </button>
          </div>
        </header>

        <div class="admin-tabs reveal" role="tablist">
          <button type="button" class="admin-tab is-active" data-tab="instance" role="tab">
            <i class="ph ph-cpu" aria-hidden="true"></i> App Instance
          </button>
          <button type="button" class="admin-tab" data-tab="datasource" role="tab">
            <i class="ph ph-database" aria-hidden="true"></i> SQL 数据源
          </button>
        </div>

        <section class="admin-panel reveal" data-panel></section>

        <div class="admin-toast" data-toast hidden></div>
      </div>
    </div>
  `;
}

function bindAdmin(container, local) {
  container.querySelector('#adminToken').addEventListener('change', (event) => {
    local.tokens[local.tab] = event.target.value.trim();
    localStorage.setItem(tokenKey(local.tab), local.tokens[local.tab]);
    showToast(local, '令牌已保存');
    loadActive(local);
  });

  container.querySelectorAll('.admin-tab').forEach((tab) => {
    tab.addEventListener('click', () => switchTab(local, tab.getAttribute('data-tab')));
  });

  container.querySelector('[data-refresh]').addEventListener('click', () => loadActive(local));

  container.querySelector('[data-panel]').addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const panel = local.tab;
    const action = button.getAttribute('data-action');
    if (panel === 'instance') handleInstanceAction(local, action, button);
    else handleDatasourceAction(local, action, button);
  });
}

function switchTab(local, tab) {
  if (tab === local.tab) return;
  local.tab = tab;
  local.root.querySelectorAll('.admin-tab').forEach((t) => {
    t.classList.toggle('is-active', t.getAttribute('data-tab') === tab);
  });
  syncTokenField(local);
  loadActive(local);
}

function syncTokenField(local) {
  const input = local.root.querySelector('#adminToken');
  input.value = local.tokens[local.tab] || '';
  const label = local.root.querySelector('[data-token-label]');
  label.textContent = local.tab === 'instance' ? 'App Instance 管理令牌' : 'SQL 数据源管理令牌';
}

async function loadActive(local) {
  if (local.tab === 'instance') await loadInstances(local);
  else await loadDatasources(local);
}

// ===== App Instance 面板 =====

async function loadInstances(local) {
  const panel = local.root.querySelector('[data-panel]');
  if (!local.tokens.instance) {
    panel.innerHTML = stateHtml('ph-lock-key', '请先在右上角填写 App Instance 管理令牌', '');
    return;
  }
  panel.innerHTML = instancePanelShell(local, true);
  try {
    local.instanceList = await requestJson(local, '/api/admin/app-instances');
    local.listError = '';
  } catch (error) {
    local.instanceList = [];
    local.listError = error.message;
  }
  panel.innerHTML = instancePanelShell(local, false);
}

function instancePanelShell(local, loading) {
  return `
    <div class="admin-panel-head">
      <div>
        <p class="panel-label">App Instance</p>
        <h2>Arthas 接入实例</h2>
      </div>
      <div class="admin-filters">
        <button type="button" class="primary-button" data-action="new">
          <i class="ph ph-plus" aria-hidden="true"></i>新建实例
        </button>
      </div>
    </div>
    <div class="admin-list" data-list>
      ${loading ? skeletonHtml() : instanceListHtml(local)}
    </div>
  `;
}

function instanceListHtml(local) {
  if (local.listError) return stateHtml('ph-warning-circle', local.listError, 'is-error');
  if (!local.instanceList.length) return stateHtml('ph-cpu', '暂无实例，点击「新建实例」添加', '');
  return local.instanceList.map(instanceCardHtml).join('');
}

function instanceCardHtml(item) {
  const status = INSTANCE_STATUS[item.status] || { label: item.status, tone: 'neutral' };
  const envLabel = ENV_LABELS[String(item.env).toLowerCase()] || item.env;
  const meta = [
    ['环境', envLabel, false],
    ['地址', item.arthasUrl || `${item.ip}:${item.arthasHttpPort}`, false],
    ['接入', item.accessMode || '—', false],
    ['Agent', item.arthasAgentId || '—', !item.arthasAgentId],
    ...(item.accessMode === 'TUNNEL' ? [] : [
      ['端口', String(item.arthasHttpPort ?? '—'), false],
      ['认证', item.arthasUsername || '无', !item.arthasUsername],
      ['密码', item.passwordConfigured ? '已配置' : '未配置', !item.passwordConfigured]
    ]),
    ['更新', formatDateTime(item.updatedAt), false]
  ];
  return `
    <article class="admin-card" data-id="${escapeHtml(item.id)}">
      <div class="admin-card-head">
        <div class="admin-card-title">
          <strong>${escapeHtml(item.appName)} <span class="admin-meta-value is-muted">(${escapeHtml(item.appId)})</span></strong>
          <small>id: ${escapeHtml(item.id)}</small>
        </div>
        <div class="admin-status-stack">
          <span class="admin-status" data-tone="${status.tone}">${status.label}</span>
        </div>
      </div>
      <div class="admin-meta">
        ${meta.map(([label, value, muted]) => `
          <div class="admin-meta-item">
            <span class="admin-meta-label">${label}</span>
            <span class="admin-meta-value${muted ? ' is-muted' : ''}">${escapeHtml(value)}</span>
          </div>
        `).join('')}
      </div>
      <div data-test-result class="admin-test-result"></div>
      <div class="admin-card-actions">
        <button type="button" class="ghost-button" data-action="test">
          <i class="ph ph-plugs-connected" aria-hidden="true"></i>测试连接
        </button>
        <button type="button" class="ghost-button" data-action="edit">
          <i class="ph ph-pencil-simple" aria-hidden="true"></i>编辑
        </button>
        <button type="button" class="ghost-button admin-danger" data-action="delete">
          <i class="ph ph-trash" aria-hidden="true"></i>删除
        </button>
      </div>
    </article>
  `;
}

function handleInstanceAction(local, action, button) {
  const card = button.closest('[data-id]');
  const id = card?.getAttribute('data-id');
  if (action === 'new') openInstanceModal(local, null);
  if (action === 'edit') {
    const item = local.instanceList.find((i) => String(i.id) === id);
    if (item) openInstanceModal(local, item);
  }
  if (action === 'test') testInstance(local, id, card);
  if (action === 'delete') deleteInstance(local, id);
}

async function testInstance(local, id, card) {
  const resultEl = card.querySelector('[data-test-result]');
  resultEl.className = 'admin-test-result';
  resultEl.textContent = '测试中...';
  try {
    const result = await requestJson(local, `/api/admin/app-instances/${id}/test`, { method: 'POST' });
    resultEl.className = `admin-test-result ${result.ok ? 'is-ok' : 'is-fail'}`;
    resultEl.textContent = `${result.ok ? '✓' : '✗'} ${result.message}（${result.latencyMs} ms）`;
  } catch (error) {
    resultEl.className = 'admin-test-result is-fail';
    resultEl.textContent = `✗ ${error.message}`;
  }
}

async function deleteInstance(local, id) {
  if (!confirm('确认删除该实例？若已有 Arthas 诊断记录将无法删除，请改用停用。')) return;
  try {
    await requestJson(local, `/api/admin/app-instances/${id}`, { method: 'DELETE' });
    showToast(local, '已删除');
    loadInstances(local);
  } catch (error) {
    showToast(local, error.message, true);
  }
}

function openInstanceModal(local, item) {
  if (!requireToken(local)) return;
  const isEdit = Boolean(item);
  const overlay = document.createElement('div');
  overlay.className = 'admin-modal-overlay';
  overlay.innerHTML = `
    <div class="admin-modal" role="dialog" aria-modal="true">
      <div class="admin-modal-head">
        <h3>${isEdit ? '编辑实例' : '新建实例'}</h3>
        <button type="button" class="ghost-button" data-close>
          <i class="ph ph-x" aria-hidden="true"></i>
        </button>
      </div>
      <form class="admin-form" data-form>
        <div class="admin-field-row">
          <label class="admin-field">
            <span>appId</span>
            <input name="appId" required value="${escapeHtml(item?.appId || '')}" placeholder="字母开头，2-64 位" />
          </label>
          <label class="admin-field">
            <span>appName</span>
            <input name="appName" required value="${escapeHtml(item?.appName || '')}" />
          </label>
          <label class="admin-field">
            <span>env</span>
            <input name="env" required value="${escapeHtml(item?.env || '')}" placeholder="如 prod" />
          </label>
        </div>
        <div class="admin-field-row">
          <label class="admin-field" data-http-field>
            <span>ip</span>
            <input name="ip" value="${escapeHtml(item?.ip || '')}" placeholder="如 10.0.0.1" />
          </label>
          <label class="admin-field" data-http-field>
            <span>arthasHttpPort</span>
            <input name="arthasHttpPort" type="number" min="1" max="65535" value="${escapeHtml(item?.arthasHttpPort || '')}" placeholder="如 8563" />
          </label>
          <label class="admin-field">
            <span>accessMode</span>
            <select name="accessMode">
              ${ACCESS_MODES.map((m) => `<option value="${m}" ${(item?.accessMode || 'HTTP') === m ? 'selected' : ''}>${m}</option>`).join('')}
            </select>
          </label>
          <label class="admin-field">
            <span>status</span>
            <select name="status">
              <option value="ONLINE" ${(item?.status || 'ONLINE') === 'ONLINE' ? 'selected' : ''}>ONLINE（在线）</option>
              <option value="OFFLINE" ${item?.status === 'OFFLINE' ? 'selected' : ''}>OFFLINE（停用）</option>
            </select>
          </label>
        </div>
        <div class="admin-field-row">
          <label class="admin-field" data-http-field>
            <span>arthasUsername</span>
            <input name="arthasUsername" value="${escapeHtml(item?.arthasUsername || '')}" placeholder="无认证可留空" />
          </label>
          <label class="admin-field" data-http-field>
            <span>arthasPassword</span>
            <input name="arthasPassword" type="password" placeholder="${isEdit ? '留空则不修改' : '无认证可留空'}" autocomplete="new-password" />
          </label>
          <label class="admin-field" data-tunnel-field>
            <span>arthasAgentId</span>
            <input name="arthasAgentId" value="${escapeHtml(item?.arthasAgentId || '')}" placeholder="如 order-service-prod-01" />
          </label>
        </div>
        <div class="admin-form-actions">
          <span class="admin-hint">${isEdit ? '密码留空保留原密文' : '密码经 AES-GCM 加密存储'}</span>
          <button type="button" class="ghost-button" data-close>取消</button>
          <button type="submit" class="primary-button">${isEdit ? '保存' : '创建'}</button>
        </div>
      </form>
    </div>
  `;
  local.root.appendChild(overlay);
  const accessModeSelect = overlay.querySelector('[name="accessMode"]');
  const syncAccessModeFields = () => {
    const tunnel = accessModeSelect.value === 'TUNNEL';
    overlay.querySelectorAll('[data-http-field]').forEach((field) => { field.hidden = tunnel; });
    overlay.querySelectorAll('[data-tunnel-field]').forEach((field) => { field.hidden = !tunnel; });
    overlay.querySelector('[name="ip"]').required = !tunnel;
    overlay.querySelector('[name="arthasHttpPort"]').required = !tunnel;
    overlay.querySelector('[name="arthasAgentId"]').required = tunnel;
  };
  accessModeSelect.addEventListener('change', syncAccessModeFields);
  syncAccessModeFields();
  const close = () => overlay.remove();
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay || event.target.closest('[data-close]')) close();
  });
  overlay.querySelector('[data-form]').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const data = Object.fromEntries(new FormData(form).entries());
    if (data.accessMode === 'TUNNEL') {
      data.ip = null;
      data.arthasHttpPort = null;
      data.arthasUsername = '';
      delete data.arthasPassword;
    } else {
      data.arthasHttpPort = Number(data.arthasHttpPort);
      data.arthasAgentId = '';
    }
    if (!data.arthasPassword) delete data.arthasPassword;
    if (!data.arthasUsername) data.arthasUsername = '';
    if (!data.arthasAgentId) data.arthasAgentId = '';
    try {
      if (isEdit) {
        await requestJson(local, `/api/admin/app-instances/${item.id}`, { method: 'PUT', body: JSON.stringify(data) });
        showToast(local, '已保存');
      } else {
        await requestJson(local, '/api/admin/app-instances', { method: 'POST', body: JSON.stringify(data) });
        showToast(local, '已创建');
      }
      close();
      loadInstances(local);
    } catch (error) {
      showToast(local, error.message, true);
    }
  });
}

// ===== SQL 数据源面板 =====

async function loadDatasources(local) {
  const panel = local.root.querySelector('[data-panel]');
  if (!local.tokens.datasource) {
    panel.innerHTML = stateHtml('ph-lock-key', '请先在右上角填写 SQL 数据源管理令牌', '');
    return;
  }
  panel.innerHTML = datasourcePanelShell(local, true);
  try {
    local.datasourceList = await requestJson(local, '/api/admin/sql/datasources');
    local.listError = '';
  } catch (error) {
    local.datasourceList = [];
    local.listError = error.message;
  }
  panel.innerHTML = datasourcePanelShell(local, false);
}

function datasourcePanelShell(local, loading) {
  return `
    <div class="admin-panel-head">
      <div>
        <p class="panel-label">SQL 数据源</p>
        <h2>SQL 诊断目标库（只读连接）</h2>
      </div>
      <div class="admin-filters">
        <button type="button" class="primary-button" data-action="new">
          <i class="ph ph-plus" aria-hidden="true"></i>新建数据源
        </button>
      </div>
    </div>
    <div class="admin-list" data-list>
      ${loading ? skeletonHtml() : datasourceListHtml(local)}
    </div>
  `;
}

function datasourceListHtml(local) {
  if (local.listError) return stateHtml('ph-warning-circle', local.listError, 'is-error');
  if (!local.datasourceList.length) return stateHtml('ph-database', '暂无数据源，点击「新建数据源」添加', '');
  return local.datasourceList.map(datasourceCardHtml).join('');
}

function datasourceCardHtml(item) {
  const status = DATASOURCE_STATUS[item.status] || { label: item.status, tone: 'neutral' };
  const envLabel = ENV_LABELS[String(item.env).toLowerCase()] || item.env;
  const meta = [
    ['应用', item.appId || '—', !item.appId],
    ['环境', envLabel, false],
    ['类型', item.dbType || '—', false],
    ['JDBC', item.jdbcUrlMasked || '—', false],
    ['用户', item.username || '—', !item.username],
    ['密码', item.passwordConfigured ? '已配置' : '未配置', !item.passwordConfigured],
    ['更新', formatDateTime(item.updatedAt), false]
  ];
  return `
    <article class="admin-card" data-id="${escapeHtml(item.id)}">
      <div class="admin-card-head">
        <div class="admin-card-title">
          <strong>${escapeHtml(item.datasourceName)} <span class="admin-meta-value is-muted">(${escapeHtml(item.datasourceCode)})</span></strong>
          <small>id: ${escapeHtml(item.id)}</small>
        </div>
        <div class="admin-status-stack">
          <span class="admin-status" data-tone="${status.tone}">${status.label}</span>
        </div>
      </div>
      <div class="admin-meta">
        ${meta.map(([label, value, muted]) => `
          <div class="admin-meta-item">
            <span class="admin-meta-label">${label}</span>
            <span class="admin-meta-value${muted ? ' is-muted' : ''}">${escapeHtml(value)}</span>
          </div>
        `).join('')}
      </div>
      <div data-test-result class="admin-test-result"></div>
      <div class="admin-card-actions">
        <button type="button" class="ghost-button" data-action="test">
          <i class="ph ph-plugs-connected" aria-hidden="true"></i>测试连接
        </button>
        <button type="button" class="ghost-button" data-action="edit">
          <i class="ph ph-pencil-simple" aria-hidden="true"></i>编辑
        </button>
        <button type="button" class="ghost-button admin-danger" data-action="delete">
          <i class="ph ph-trash" aria-hidden="true"></i>删除
        </button>
      </div>
    </article>
  `;
}

function handleDatasourceAction(local, action, button) {
  const card = button.closest('[data-id]');
  const id = card?.getAttribute('data-id');
  if (action === 'new') openDatasourceModal(local, null);
  if (action === 'edit') {
    const item = local.datasourceList.find((i) => String(i.id) === id);
    if (item) openDatasourceModal(local, item);
  }
  if (action === 'test') testDatasource(local, id, card);
  if (action === 'delete') deleteDatasource(local, id);
}

async function testDatasource(local, id, card) {
  const resultEl = card.querySelector('[data-test-result]');
  resultEl.className = 'admin-test-result';
  resultEl.textContent = '测试中...';
  try {
    await requestJson(local, `/api/admin/sql/datasources/${id}/test`, { method: 'POST' });
    resultEl.className = 'admin-test-result is-ok';
    resultEl.textContent = '✓ 连接成功';
  } catch (error) {
    resultEl.className = 'admin-test-result is-fail';
    resultEl.textContent = `✗ ${error.message}`;
  }
}

async function deleteDatasource(local, id) {
  if (!confirm('确认删除该数据源？若已有诊断记录将无法删除，请改用停用。')) return;
  try {
    await requestJson(local, `/api/admin/sql/datasources/${id}`, { method: 'DELETE' });
    showToast(local, '已删除');
    loadDatasources(local);
  } catch (error) {
    showToast(local, error.message, true);
  }
}

function openDatasourceModal(local, item) {
  if (!requireToken(local)) return;
  const isEdit = Boolean(item);
  const overlay = document.createElement('div');
  overlay.className = 'admin-modal-overlay';
  overlay.innerHTML = `
    <div class="admin-modal" role="dialog" aria-modal="true">
      <div class="admin-modal-head">
        <h3>${isEdit ? '编辑数据源' : '新建数据源'}</h3>
        <button type="button" class="ghost-button" data-close>
          <i class="ph ph-x" aria-hidden="true"></i>
        </button>
      </div>
      <form class="admin-form" data-form>
        <div class="admin-field-row">
          <label class="admin-field">
            <span>datasourceCode</span>
            <input name="datasourceCode" required value="${escapeHtml(item?.datasourceCode || '')}" placeholder="字母开头，2-64 位" />
          </label>
          <label class="admin-field">
            <span>datasourceName</span>
            <input name="datasourceName" required value="${escapeHtml(item?.datasourceName || '')}" />
          </label>
          <label class="admin-field">
            <span>appId（绑定应用）</span>
            <select name="appId" required>
              <option value="">请选择应用</option>
              ${appOptionLabels(local).map((o) => `<option value="${escapeHtml(o.value)}" ${(item?.appId || '') === o.value ? 'selected' : ''}>${escapeHtml(o.label)}</option>`).join('')}
            </select>
          </label>
          <label class="admin-field">
            <span>env</span>
            <input name="env" required value="${escapeHtml(item?.env || '')}" placeholder="如 prod" />
          </label>
        </div>
        <label class="admin-field admin-field-wide">
          <span>jdbcUrl</span>
          <input name="jdbcUrl" ${isEdit ? '' : 'required'} value="" placeholder="${isEdit ? '留空保留原 URL；修改时请输入完整 URL' : 'jdbc:mysql://host:3306/db?...'}" autocomplete="off" />
        </label>
        <div class="admin-field-row">
          <label class="admin-field">
            <span>username</span>
            <input name="username" required value="${escapeHtml(item?.username || '')}" />
          </label>
          <label class="admin-field">
            <span>password</span>
            <input name="password" type="password" placeholder="${isEdit ? '留空则不修改' : '新建必填'}" autocomplete="new-password" />
          </label>
          <label class="admin-field">
            <span>status</span>
            <select name="status">
              <option value="ENABLED" ${(item?.status || 'ENABLED') === 'ENABLED' ? 'selected' : ''}>ENABLED（启用）</option>
              <option value="DISABLED" ${item?.status === 'DISABLED' ? 'selected' : ''}>DISABLED（停用）</option>
            </select>
          </label>
        </div>
        <div class="admin-form-actions">
          <span class="admin-hint">${isEdit ? '密码和 jdbcUrl 留空均保留原值；修改 URL 时必须填写完整地址' : '密码经 AES-GCM 加密存储'}</span>
          <button type="button" class="ghost-button" data-close>取消</button>
          <button type="submit" class="primary-button">${isEdit ? '保存' : '创建'}</button>
        </div>
      </form>
    </div>
  `;
  local.root.appendChild(overlay);
  const close = () => overlay.remove();
  overlay.addEventListener('click', (event) => {
    if (event.target === overlay || event.target.closest('[data-close]')) close();
  });
  overlay.querySelector('[data-form]').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = event.target;
    const data = Object.fromEntries(new FormData(form).entries());
    if (!data.jdbcUrl?.trim()) delete data.jdbcUrl;
    if (!data.password) delete data.password;
    try {
      if (isEdit) {
        await requestJson(local, `/api/admin/sql/datasources/${item.id}`, { method: 'PUT', body: JSON.stringify(data) });
        showToast(local, '已保存');
      } else {
        await requestJson(local, '/api/admin/sql/datasources', { method: 'POST', body: JSON.stringify(data) });
        showToast(local, '已创建');
      }
      close();
      loadDatasources(local);
    } catch (error) {
      showToast(local, error.message, true);
    }
  });
}

// ===== 共享辅助 =====

function tokenKey(tab) {
  return TOKEN_KEYS[tab];
}

// 加载在线 app 实例选项（无需令牌），供数据源「绑定应用」下拉使用。
async function loadAppOptions(local) {
  try {
    const options = await fetch(`${local.apiBase}/api/app-instances/options`).then(handleResponse);
    local.appOptions = Array.isArray(options)
      ? options.filter((o) => o && o.appId && o.env)
      : [];
  } catch {
    local.appOptions = [];
  }
}

function appOptionLabels(local) {
  const services = new Map();
  local.appOptions.forEach((option) => {
    if (!services.has(option.appId)) {
      services.set(option.appId, option.appName || option.appId);
    }
  });
  return [...services.entries()].map(([appId, name]) => ({
    value: appId,
    label: name && name !== appId ? `${name} (${appId})` : appId
  }));
}

function requireToken(local) {
  if (!local.tokens[local.tab]) {
    showToast(local, '请先填写管理令牌', true);
    return false;
  }
  return true;
}

async function requestJson(local, path, options = {}) {
  const headers = { 'X-Diagnosis-Admin-Token': local.tokens[local.tab], ...(options.headers || {}) };
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

function stateHtml(icon, text, extraClass) {
  return `<div class="admin-state ${extraClass}"><i class="ph ${icon}" aria-hidden="true"></i><span>${escapeHtml(text)}</span></div>`;
}

function skeletonHtml() {
  return Array.from({ length: 3 }).map(() => `
    <div class="admin-skeleton">
      <div class="admin-skeleton-line mid"></div>
      <div class="admin-skeleton-line short"></div>
      <div class="admin-skeleton-line"></div>
    </div>
  `).join('');
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

function formatDateTime(value) {
  if (!value) return '—';
  return String(value).replace('T', ' ').split('.')[0];
}

// 共享侧边导航：顶部固定品牌「性能因果图谱」，上方「概览」，下方「因果诊断」。
// 每个入口都带图标与文字说明，图标语义与文案匹配。

export function railHtml(active, { bottom } = {}) {
  const overviewActive = active === 'overview' ? ' is-active' : '';
  const studioActive = active === 'studio' ? ' is-active' : '';
  const knowledgeActive = active === 'knowledge' ? ' is-active' : '';
  const railBottom = bottom ?? `
    <div class="rail-status">
      <span class="rail-state" data-state="ready" aria-hidden="true"></span>
      <span class="rail-status-text">系统就绪</span>
    </div>
  `;
  return `
    <aside class="side-rail" aria-label="主导航">
      <div class="rail-brand" title="性能因果图谱">
        <span class="rail-brand-mark" aria-hidden="true">
          <i class="ph ph-graph"></i>
        </span>
        <span class="rail-brand-text">
          <strong>性能因果图谱</strong>
          <small>Causal Map</small>
        </span>
      </div>

      <nav class="rail-nav">
        <p class="rail-section-label">导航</p>
        <button class="rail-item${overviewActive}" type="button" data-route="/overview" aria-label="概览" title="概览">
          <span class="rail-item-icon" aria-hidden="true"><i class="ph ph-squares-four"></i></span>
          <span class="rail-item-text">
            <strong>概览</strong>
            <small>事件回顾与统计</small>
          </span>
        </button>
        <button class="rail-item${studioActive}" type="button" data-route="/studio" aria-label="因果诊断" title="因果诊断">
          <span class="rail-item-icon" aria-hidden="true"><i class="ph ph-stethoscope"></i></span>
          <span class="rail-item-text">
            <strong>因果诊断</strong>
            <small>AI + Arthas 现场</small>
          </span>
        </button>
        <button class="rail-item${knowledgeActive}" type="button" data-route="/knowledge" aria-label="知识库" title="知识库">
          <span class="rail-item-icon" aria-hidden="true"><i class="ph ph-books"></i></span>
          <span class="rail-item-text">
            <strong>知识库</strong>
            <small>RAG 参考知识</small>
          </span>
        </button>
      </nav>

      <div class="rail-bottom">
        ${railBottom}
      </div>
    </aside>
  `;
}

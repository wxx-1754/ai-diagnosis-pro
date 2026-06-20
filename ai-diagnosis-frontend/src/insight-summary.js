const ROOT_CAUSE_LIMIT = 72;
const REASON_LIMIT = 56;
const EXPECTED_EFFECT_LIMIT = 100;
const ACTION_LIMIT = 56;
const MAX_REASONS = 3;
const MAX_ACTIONS = 3;

export function createEmptyInsightSummary() {
  return {
    rootCause: '',
    specificReasons: [],
    expectedEffect: '',
    recommendedActions: []
  };
}

export function extractInsightSummary(data) {
  if (!data) return createEmptyInsightSummary();

  const direct = data.insightSummary || data.data?.insightSummary;
  if (direct) return normalizeInsightSummary(direct);

  const reportJson = data.reportJson || data.data?.reportJson;
  if (typeof reportJson === 'string') {
    try {
      return normalizeInsightSummary(JSON.parse(reportJson));
    } catch {
      return createEmptyInsightSummary();
    }
  }

  return createEmptyInsightSummary();
}

export function normalizeInsightSummary(summary) {
  const source = summary && typeof summary === 'object' ? summary : {};
  const actions = Array.isArray(source.recommendedActions)
    ? source.recommendedActions
    : [];
  const reasons = Array.isArray(source.specificReasons)
    ? source.specificReasons
    : [];

  return {
    rootCause: truncate(clean(source.rootCause), ROOT_CAUSE_LIMIT),
    specificReasons: reasons
      .map(clean)
      .filter(Boolean)
      .slice(0, MAX_REASONS)
      .map((reason) => truncate(reason, REASON_LIMIT)),
    expectedEffect: truncate(clean(source.expectedEffect), EXPECTED_EFFECT_LIMIT),
    recommendedActions: actions
      .map(clean)
      .filter(Boolean)
      .slice(0, MAX_ACTIONS)
      .map((action) => truncate(action, ACTION_LIMIT))
  };
}

function clean(value) {
  return String(value || '')
    .replace(/[#*_`>~]/g, '')
    .replace(/^\s*\d+[.、)]\s*/, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function truncate(value, maxLength) {
  return value.length > maxLength ? `${value.slice(0, maxLength - 1)}…` : value;
}

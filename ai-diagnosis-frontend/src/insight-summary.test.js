import test from 'node:test';
import assert from 'node:assert/strict';
import { extractInsightSummary, normalizeInsightSummary } from './insight-summary.js';

test('normalizes and strictly limits AI insight fields', () => {
  const summary = normalizeInsightSummary({
    rootCause: '**Mapper 重复查询数据库导致接口耗时升高。**',
    specificReasons: [
      '1. trace 显示 Mapper 占总耗时 82%。',
      '2. 同一请求内重复执行相同查询。',
      '3. 数据库等待时间明显偏高。',
      '4. 不应展示'
    ],
    expectedEffect: '预计 P95 下降 20%–30%，最终需压测验证。',
    recommendedActions: [
      '1. 合并重复查询并增加单元测试。',
      '2. 灰度发布后观察 P95 和错误率。',
      '3. 未达预期时回滚。',
      '4. 不应展示'
    ]
  });

  assert.equal(summary.rootCause, 'Mapper 重复查询数据库导致接口耗时升高。');
  assert.equal(summary.specificReasons.length, 3);
  assert.ok(summary.specificReasons.every((reason) => reason.length <= 56));
  assert.equal(summary.recommendedActions.length, 3);
  assert.ok(summary.recommendedActions.every((action) => action.length <= 56));
});

test('extracts summary from persisted reportJson', () => {
  const summary = extractInsightSummary({
    reportJson: JSON.stringify({
      rootCause: '连接池等待导致接口变慢。',
      specificReasons: ['线程持续等待空闲数据库连接。'],
      expectedEffect: '需修复后验证。',
      recommendedActions: ['调整连接池并灰度验证。']
    })
  });

  assert.equal(summary.rootCause, '连接池等待导致接口变慢。');
  assert.equal(summary.specificReasons.length, 1);
  assert.equal(summary.recommendedActions.length, 1);
});

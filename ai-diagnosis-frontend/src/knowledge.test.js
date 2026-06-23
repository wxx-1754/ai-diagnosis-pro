import test from 'node:test';
import assert from 'node:assert/strict';

import { isPendingReview, validateReviewInput } from './knowledge-review.js';

test('approval requires reviewer, title and content', () => {
  assert.equal(validateReviewInput({
    action: 'APPROVE',
    reviewedBy: '',
    title: '案例',
    content: '正文'
  }), '请输入审核人');
  assert.equal(validateReviewInput({
    action: 'APPROVE',
    reviewedBy: 'reviewer',
    title: '',
    content: '正文'
  }), '批准时标题和正文不能为空');
});

test('rejection requires a reason', () => {
  assert.equal(validateReviewInput({
    action: 'REJECT',
    reviewedBy: 'reviewer',
    comment: ''
  }), '驳回时必须填写原因');
  assert.equal(validateReviewInput({
    action: 'REJECT',
    reviewedBy: 'reviewer',
    comment: '证据不足'
  }), '');
});

test('pending review status remains editable across whitespace and case differences', () => {
  assert.equal(isPendingReview('PENDING_REVIEW'), true);
  assert.equal(isPendingReview(' pending_review '), true);
  assert.equal(isPendingReview('APPROVED'), false);
});

export function validateReviewInput(body) {
  if (!body.reviewedBy) return '请输入审核人';
  if (body.action === 'APPROVE' && (!body.title || !String(body.content || '').trim())) {
    return '批准时标题和正文不能为空';
  }
  if (body.action === 'REJECT' && !body.comment) return '驳回时必须填写原因';
  return '';
}

export function isPendingReview(qualityStatus) {
  return String(qualityStatus || '').trim().toUpperCase() === 'PENDING_REVIEW';
}

// ==================== 项目生命周期状态 ====================
const PROJECT_STATUS_MAP = {
  DRAFT: {
    label: '草稿',
    tagType: 'info',
    accent: 'slate',
  },
  UNDER_REVIEW: {
    label: '待审核',
    tagType: 'warning',
    accent: 'orange',
  },
  REVISION_REQUIRED: {
    label: '退回修改',
    tagType: 'danger',
    accent: 'red',
  },
  APPROVED: {
    label: '审核通过',
    tagType: 'success',
    accent: 'green',
  },
}

// ==================== 通知解析状态 ====================
const NOTICE_PARSE_STATUS_MAP = {
  DRAFT: {
    label: '待解析',
    tagType: 'info',
    accent: 'slate',
  },
  PARSING: {
    label: '解析中',
    tagType: 'warning',
    accent: 'orange',
  },
  PARSED: {
    label: '已解析',
    tagType: 'success',
    accent: 'green',
  },
  FAILED: {
    label: '解析失败',
    tagType: 'danger',
    accent: 'red',
  },
}

// ==================== 通知发布状态 ====================
const NOTICE_PUBLISH_STATUS_MAP = {
  DRAFT: {
    label: '未发布',
    tagType: 'info',
    accent: 'slate',
  },
  PUBLISHED: {
    label: '已发布',
    tagType: 'success',
    accent: 'green',
  },
  ARCHIVED: {
    label: '已归档',
    tagType: 'info',
    accent: 'slate',
  },
}

// ==================== AI 检查结果（不可变） ====================
const AI_CHECK_RESULT_MAP = {
  PASSED: {
    label: 'AI检查通过',
    tagType: 'success',
    accent: 'green',
  },
  WARNING: {
    label: 'AI检查有风险',
    tagType: 'warning',
    accent: 'orange',
  },
}

// ==================== 教师材料审核决定（不可变） ====================
const MATERIAL_REVIEW_DECISION_MAP = {
  APPROVED: {
    label: '材料通过',
    tagType: 'success',
    accent: 'green',
  },
  REVISION_REQUIRED: {
    label: '需修改',
    tagType: 'danger',
    accent: 'red',
  },
}

// ==================== Agent 任务状态 ====================
const AGENT_TASK_STATUS_MAP = {
  PENDING: {
    label: '等待执行',
    tagType: 'info',
    accent: 'slate',
  },
  RUNNING: {
    label: '执行中',
    tagType: 'warning',
    accent: 'orange',
  },
  SUCCESS: {
    label: '执行成功',
    tagType: 'success',
    accent: 'green',
  },
  FAILED: {
    label: '执行失败',
    tagType: 'danger',
    accent: 'red',
  },
  TIMEOUT: {
    label: '执行超时',
    tagType: 'danger',
    accent: 'red',
  },
}

// ==================== 向后兼容的旧状态映射（迁移期间使用） ====================
const LEGACY_STATUS_MAP = {
  draft: PROJECT_STATUS_MAP.DRAFT,
  incomplete: { label: '材料待补全（旧）', tagType: 'danger', accent: 'red' },
  ready: { label: '可提交（旧）', tagType: 'success', accent: 'green' },
  submitted: { label: '已提交', tagType: 'success', accent: 'green' },
  pending: { label: '待上传', tagType: 'info', accent: 'slate' },
  pass: AI_CHECK_RESULT_MAP.PASSED,
  warning: AI_CHECK_RESULT_MAP.WARNING,
  approved: MATERIAL_REVIEW_DECISION_MAP.APPROVED,
  revision: MATERIAL_REVIEW_DECISION_MAP.REVISION_REQUIRED,
  reject: { label: '已退回（旧）', tagType: 'danger', accent: 'red' },
}

// ==================== 统一查询方法 ====================

/**
 * 解析项目状态元数据。
 */
export function resolveProjectStatus(status) {
  return PROJECT_STATUS_MAP[status] ?? { label: status || '未知状态', tagType: 'info', accent: 'slate' }
}

/**
 * 解析通知解析状态元数据。
 */
export function resolveNoticeParseStatus(status) {
  return NOTICE_PARSE_STATUS_MAP[status] ?? { label: status || '未知状态', tagType: 'info', accent: 'slate' }
}

/**
 * 解析通知发布状态元数据。
 */
export function resolveNoticePublishStatus(status) {
  return NOTICE_PUBLISH_STATUS_MAP[status] ?? { label: status || '未知状态', tagType: 'info', accent: 'slate' }
}

/**
 * 解析 AI 检查结果元数据。
 */
export function resolveAiCheckResult(result) {
  return AI_CHECK_RESULT_MAP[result] ?? { label: result || '未检查', tagType: 'info', accent: 'slate' }
}

/**
 * 解析教师材料审核决定元数据。
 */
export function resolveMaterialReviewDecision(decision) {
  return MATERIAL_REVIEW_DECISION_MAP[decision] ?? { label: decision || '未审核', tagType: 'info', accent: 'slate' }
}

/**
 * 解析 Agent 任务状态元数据。
 */
export function resolveAgentTaskStatus(status) {
  return AGENT_TASK_STATUS_MAP[status] ?? { label: status || '未知状态', tagType: 'info', accent: 'slate' }
}

/**
 * 通用状态解析（向后兼容旧状态字符串，优先匹配新枚举）。
 */
export function resolveStatusMeta(status) {
  if (!status) return { label: '未知状态', tagType: 'info', accent: 'slate' }

  // 优先匹配新枚举
  if (PROJECT_STATUS_MAP[status]) return PROJECT_STATUS_MAP[status]
  if (NOTICE_PARSE_STATUS_MAP[status]) return NOTICE_PARSE_STATUS_MAP[status]
  if (NOTICE_PUBLISH_STATUS_MAP[status]) return NOTICE_PUBLISH_STATUS_MAP[status]
  if (AI_CHECK_RESULT_MAP[status]) return AI_CHECK_RESULT_MAP[status]
  if (MATERIAL_REVIEW_DECISION_MAP[status]) return MATERIAL_REVIEW_DECISION_MAP[status]
  if (AGENT_TASK_STATUS_MAP[status]) return AGENT_TASK_STATUS_MAP[status]

  // 向后兼容旧状态
  if (LEGACY_STATUS_MAP[status]) return LEGACY_STATUS_MAP[status]

  return { label: status, tagType: 'info', accent: 'slate' }
}

// ==================== 材料完整度（展示用，不落库） ====================
export function resolveMaterialCompleteness(complete, completionRate) {
  if (complete) {
    return { label: `材料已齐 (${completionRate ?? 100}%)`, tagType: 'success', accent: 'green' }
  }
  return { label: `材料未齐 (${completionRate ?? 0}%)`, tagType: 'danger', accent: 'red' }
}

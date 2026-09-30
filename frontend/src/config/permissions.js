import { ROLES } from '@/config/roles.js'

/**
 * 前端权限常量 —— 轻量动作码 + 角色映射。
 *
 * 当前只有三个固定角色，后端也不是动态 RBAC 权限系统，
 * 因此不需要在前端构造复杂的权限码平台。用常量 + Set 即可覆盖所有场景。
 *
 * 页面中使用时配合 composables/usePermission.js 的 can() 函数，
 * 叠加角色权限 + 资源归属（isMember/isLeader/isAdvisor）+ 项目状态条件。
 */

export const ACTIONS = {
  NOTICE_UPLOAD: 'notice:upload',
  NOTICE_PARSE: 'notice:parse',
  NOTICE_CONFIRM: 'notice:confirm',
  NOTICE_PUBLISH: 'notice:publish',
  NOTICE_ARCHIVE: 'notice:archive',
  PROJECT_CREATE: 'project:create',
  PROJECT_MEMBER_MANAGE: 'project:member:manage',
  MATERIAL_UPLOAD: 'material:upload',
  AI_CHECK_RUN: 'ai-check:run',
  PROJECT_SUBMIT: 'project:submit',
  MATERIAL_REVIEW: 'material:review',
  USER_MANAGE: 'user:manage',
  AUDIT_VIEW: 'audit:view',
}

/**
 * 角色 → 允许执行的动作集合。
 */
export const ROLE_ACTIONS = {
  [ROLES.STUDENT]: new Set([
    ACTIONS.PROJECT_CREATE,
    ACTIONS.PROJECT_MEMBER_MANAGE,
    ACTIONS.MATERIAL_UPLOAD,
    ACTIONS.AI_CHECK_RUN,
    ACTIONS.PROJECT_SUBMIT,
  ]),
  [ROLES.TEACHER]: new Set([ACTIONS.MATERIAL_REVIEW]),
  [ROLES.ADMIN]: new Set(Object.values(ACTIONS)),
}

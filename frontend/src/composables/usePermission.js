import { ACTIONS, ROLE_ACTIONS } from '@/config/permissions.js'
import { normalizeRole } from '@/config/roles.js'

/**
 * 判断当前用户是否可以执行指定动作。
 *
 * 权限判断分两层：
 * 1. 角色权限（ROLE_ACTIONS）—— 该角色是否拥有该动作的基础权限
 * 2. 资源条件 —— 叠加项目成员关系、项目状态等上下文条件
 *
 * @param {string} action - ACTIONS 中的动作常量
 * @param {{
 *   role?: string,
 *   isMember?: boolean,
 *   isLeader?: boolean,
 *   isAdvisor?: boolean,
 *   projectStatus?: string,
 * }} context - 资源上下文
 * @returns {boolean}
 *
 * @example
 * import { can } from '@/composables/usePermission.js'
 * import { ACTIONS } from '@/config/permissions.js'
 *
 * const canUpload = can(ACTIONS.MATERIAL_UPLOAD, {
 *   role: 'student',
 *   isMember: true,
 *   projectStatus: 'DRAFT',
 * })
 */
export function can(action, context = {}) {
  const roleActions = ROLE_ACTIONS[normalizeRole(context.role)]
  if (!roleActions || !roleActions.has(action)) {
    return false
  }

  // 叠加资源条件
  switch (action) {
    case ACTIONS.MATERIAL_UPLOAD:
    case ACTIONS.AI_CHECK_RUN:
      return context.isMember && ['DRAFT', 'REVISION_REQUIRED'].includes(context.projectStatus)

    case ACTIONS.PROJECT_SUBMIT:
      return context.isLeader && ['DRAFT', 'REVISION_REQUIRED'].includes(context.projectStatus)

    case ACTIONS.PROJECT_MEMBER_MANAGE:
      return context.isLeader

    case ACTIONS.MATERIAL_REVIEW:
      return context.isAdvisor && context.projectStatus === 'UNDER_REVIEW'

    default:
      return true
  }
}

import { getAuthState } from '@/state/auth.js'

const BASE_REQUIREMENTS = [
  {
    requirementName: '项目申报书',
    description: '按统一模板填写项目背景、创新点、实施计划和预期成果。',
  },
  {
    requirementName: '团队成员信息表',
    description: '包含负责人、成员分工、联系方式及指导教师基础信息。',
  },
  {
    requirementName: '指导教师意见表',
    description: '由指导教师填写审核意见并确认签字。',
  },
]

const initialState = createInitialState()
const state = clone(initialState)

function futureDeadline(days) {
  const date = new Date(Date.now() + days * 24 * 60 * 60 * 1000)
  date.setHours(23, 59, 59, 0)
  const two = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}T23:59:59`
}

function createInitialState() {
  const demo = {
    counters: {
      noticeId: 3,
      parseDraftId: 1,
      noticeTaskId: 100,
      requirementId: 4,
      projectId: 6,
      materialId: 4,
      reviewId: 2,
      memberId: 13,
      fileId: 5,
      notifyMsgId: 13,
      aiCheckId: 2,
    },
    users: [
      { userId: 1, username: 'admin', realName: '系统管理员', role: 'admin' },
      { userId: 2, username: 'teacher01', realName: '张老师', role: 'teacher' },
      { userId: 3, username: 'student01', realName: '李同学', role: 'student' },
      { userId: 4, username: 'student02', realName: '王同学', role: 'student' },
      { userId: 5, username: 'student03', realName: '赵同学', role: 'student' },
      { userId: 6, username: 'student04', realName: '陈同学', role: 'student' },
      { userId: 7, username: 'student05', realName: '刘同学', role: 'student' },
      { userId: 8, username: 'teacher02', realName: '周老师', role: 'teacher' },
      { userId: 9, username: 'teacher03', realName: '吴老师', role: 'teacher' },
      { userId: 10, username: 'student06', realName: '孙同学', role: 'student' },
      { userId: 11, username: 'student07', realName: '黄同学', role: 'student' },
      { userId: 12, username: 'student08', realName: '杨同学', role: 'student' },
    ],
    fileAssets: [
      { fileId: 1, bizType: 'notice', fileName: '大学生创新创业训练计划项目申报通知.pdf' },
      { fileId: 2, bizType: 'material', fileName: '项目申报书.docx' },
      { fileId: 3, bizType: 'material', fileName: '团队成员信息表.xlsx' },
      { fileId: 4, bizType: 'material', fileName: '指导教师意见表.pdf' },
    ],
    notices: [
      {
        noticeId: 1,
        title: '大学生创新创业训练计划项目申报通知',
        organizer: '创新创业学院',
        deadline: futureDeadline(30),
        targetGroup: '全日制本科生团队',
        rawText: '围绕创新创业训练计划项目开展申报，需提交项目申报书、团队成员信息表与指导教师意见表。',
        aiSummary: '系统已识别出截止时间、适用对象及 3 项必交申报材料。',
        noticeFileId: 1,
        noticeType: 'COMPETITION',
        parseStatus: 'PARSED',
        publishStatus: 'PUBLISHED',
        confirmedBy: 1,
        confirmedAt: '2026-04-28T09:20:00',
        publishedAt: '2026-04-28T09:30:00',
        createdAt: '2026-04-28T09:00:00',
      },
      {
        noticeId: 2,
        title: '2026 年校园科技创新竞赛通知',
        organizer: '校团委',
        deadline: futureDeadline(60),
        targetGroup: '全校本科生及研究生团队',
        rawText: '面向全校学生团队征集科技创新项目，材料要求以 AI 解析并经管理员确认后的清单为准。',
        aiSummary: '待解析',
        noticeFileId: null,
        noticeType: 'COMPETITION',
        parseStatus: 'DRAFT',
        publishStatus: 'DRAFT',
        confirmedBy: null,
        confirmedAt: null,
        publishedAt: null,
        createdAt: '2026-07-01T10:00:00',
      },
    ],
    requirements: [
      {
        requirementId: 1,
        noticeId: 1,
        requirementName: '项目申报书',
        isRequired: 1,
        description: '按统一模板填写项目背景、创新点、实施计划和预期成果。',
        sortNo: 1,
        status: 'ACTIVE',
      },
      {
        requirementId: 2,
        noticeId: 1,
        requirementName: '团队成员信息表',
        isRequired: 1,
        description: '包含负责人、成员分工、联系方式及指导教师基础信息。',
        sortNo: 2,
        status: 'ACTIVE',
      },
      {
        requirementId: 3,
        noticeId: 1,
        requirementName: '指导教师意见表',
        isRequired: 1,
        description: '由指导教师填写审核意见并确认签字。',
        sortNo: 3,
        status: 'ACTIVE',
      },
    ],
    projects: [
      {
        projectId: 1,
        noticeId: 1,
        leaderId: 3,
        projectName: '基于大模型的校园竞赛申报材料智能核验助手',
        teamName: '逐梦智审队',
        status: 'DRAFT',
        deadline: futureDeadline(30),
        completionRate: 100,
      },
      {
        projectId: 2,
        noticeId: 1,
        leaderId: 4,
        projectName: '智能校园导航系统',
        teamName: '智航先锋队',
        status: 'UNDER_REVIEW',
        deadline: futureDeadline(30),
        completionRate: 100,
      },
      {
        projectId: 3,
        noticeId: 1,
        leaderId: 5,
        projectName: '基于深度学习的课堂专注度分析平台',
        teamName: '慧眼识课队',
        status: 'DRAFT',
        deadline: futureDeadline(30),
        completionRate: 0,
      },
      {
        projectId: 4,
        noticeId: 1,
        leaderId: 6,
        projectName: '校园二手书交易小程序',
        teamName: '书途同归队',
        status: 'APPROVED',
        deadline: futureDeadline(30),
        completionRate: 100,
      },
      {
        projectId: 5,
        noticeId: 1,
        leaderId: 7,
        projectName: '基于区块链的学分认证系统',
        teamName: '链上青春队',
        status: 'REVISION_REQUIRED',
        deadline: futureDeadline(30),
        completionRate: 66.67,
      },
    ],
    projectMembers: [
      { memberId: 1, projectId: 1, userId: 3, memberRole: 'leader' },
      { memberId: 2, projectId: 1, userId: 2, memberRole: 'advisor' },
      { memberId: 3, projectId: 1, userId: 4, memberRole: 'member' },
      { memberId: 4, projectId: 2, userId: 4, memberRole: 'leader' },
      { memberId: 5, projectId: 2, userId: 8, memberRole: 'advisor' },
      { memberId: 6, projectId: 2, userId: 5, memberRole: 'member' },
      { memberId: 7, projectId: 3, userId: 5, memberRole: 'leader' },
      { memberId: 8, projectId: 3, userId: 2, memberRole: 'advisor' },
      { memberId: 9, projectId: 4, userId: 6, memberRole: 'leader' },
      { memberId: 10, projectId: 4, userId: 9, memberRole: 'advisor' },
      { memberId: 11, projectId: 5, userId: 7, memberRole: 'leader' },
      { memberId: 12, projectId: 5, userId: 8, memberRole: 'advisor' },
    ],
    materials: [
      {
        materialId: 1,
        projectId: 1,
        requirementId: 1,
        fileId: 2,
        fileName: '项目申报书.docx',
        submitStatus: 'submitted',
        currentVersionId: 1,
        fileHash: null,
        versionNo: 1,
        remark: '已上传项目申报书初版。',
        submittedAt: '2026-05-01T10:30:00',
      },
      {
        materialId: 2,
        projectId: 1,
        requirementId: 2,
        fileId: 3,
        fileName: '团队成员信息表.xlsx',
        submitStatus: 'submitted',
        currentVersionId: 2,
        fileHash: null,
        versionNo: 1,
        remark: '团队成员信息已填写完毕。',
        submittedAt: '2026-05-03T14:20:00',
      },
      {
        materialId: 3,
        projectId: 1,
        requirementId: 3,
        fileId: 4,
        fileName: '指导教师意见表.pdf',
        submitStatus: 'submitted',
        currentVersionId: 3,
        fileHash: null,
        versionNo: 1,
        remark: '指导教师已签字确认。',
        submittedAt: '2026-05-05T09:15:00',
      },
    ],
    reviewRecords: [
      {
        reviewId: 1,
        projectId: 1,
        reviewType: 'ai',
        reviewResult: 'WARNING',
        reviewComment: '当前仍缺少 2 项必交材料：团队成员信息表、指导教师意见表。',
        reviewerName: '智能核验引擎',
        createdAt: '2026-05-01T11:20:00',
      },
    ],
    agentTaskLogs: initAgentTaskLogs(),
    notifyMessages: initNotifyMessages(),
    materialReviews: initMaterialReviews(),
    projectAiChecks: [
      {
        id: 1,
        projectId: 1,
        result: 'WARNING',
        issueSummary: '当前仍缺少 2 项必交材料。',
        materialVersionIds: [1],
        createdAt: '2026-05-01T11:20:00',
      },
    ],
  }
  seedDemoProjectMaterials(demo)
  for (const message of demo.notifyMessages.filter((item) => item.msgType === 'deadline')) {
    message.msgContent = `项目申报截止时间为 ${demo.notices[0].deadline.slice(0, 10)}，请在截止前完成材料提交。`
  }
  return demo
}

function seedDemoProjectMaterials(demo) {
  for (const projectId of [2, 4, 5]) {
    for (const requirementId of [1, 2, 3]) {
      if (projectId === 5 && requirementId === 3) continue
      const materialId = demo.counters.materialId++
      const fileId = demo.counters.fileId++
      const fileName = `项目${projectId}-材料${requirementId}.pdf`
      demo.fileAssets.push({ fileId, bizType: 'material', fileName })
      demo.materials.push({ materialId, projectId, requirementId, fileId, fileName,
        submitStatus: 'submitted', currentVersionId: materialId, fileHash: null,
        versionNo: 1, remark: '已提交初版', submittedAt: '2026-06-10T09:00:00' })
      if (projectId === 2 || projectId === 4) {
        demo.materialReviews.push({ reviewId: demo.counters.reviewId++, materialId,
          materialVersionId: materialId, reviewerId: projectId === 2 ? 8 : 9,
          decision: projectId === 2 && requirementId === 3 ? 'REVISION_REQUIRED' : 'APPROVED',
          comment: projectId === 2 && requirementId === 3 ? '请补充签字意见' : '内容符合要求',
          createdAt: '2026-06-12T09:00:00' })
      }
    }
  }
}

function clone(data) {
  if (typeof structuredClone === 'function') {
    return structuredClone(data)
  }

  return JSON.parse(JSON.stringify(data))
}

function nextId(key) {
  const current = state.counters[key]
  state.counters[key] += 1
  return current
}

function delayResponse(data, message = 'success', ms = 280) {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve({
        code: 200,
        message,
        data: clone(data),
        timestamp: new Date().toISOString(),
      })
    }, ms)
  })
}

function rejectResponse(message, status = 400, ms = 100) {
  return new Promise((_, reject) => {
    window.setTimeout(() => {
      reject({
        response: { status, data: { code: status, message } },
        message,
      })
    }, ms)
  })
}

function getNoticeRequirements(noticeId) {
  return state.requirements
    .filter((item) => item.noticeId === noticeId && item.status === 'ACTIVE')
    .sort((left, right) => left.sortNo - right.sortNo)
}

function getUserById(userId) {
  return state.users.find((item) => item.userId === userId)
}

function getProjectById(projectId) {
  return state.projects.find((item) => item.projectId === projectId)
}

function getNoticeById(noticeId) {
  return state.notices.find((item) => item.noticeId === noticeId)
}

function getFileName(fileId) {
  return state.fileAssets.find((item) => item.fileId === fileId)?.fileName ?? ''
}

function buildNoticeView(notice) {
  if (!notice) {
    return null
  }

  return {
    noticeId: notice.noticeId,
    title: notice.title,
    organizer: notice.organizer,
    deadline: notice.deadline,
    targetGroup: notice.targetGroup,
    rawText: notice.rawText,
    aiSummary: notice.aiSummary,
    fileId: notice.noticeFileId,
    fileName: getFileName(notice.noticeFileId),
    materialRequirements: getNoticeRequirements(notice.noticeId).map((item) => item.requirementName),
  }
}

function buildMaterialViews(projectId) {
  const project = getProjectById(projectId)
  if (!project) {
    return []
  }

  return getNoticeRequirements(project.noticeId).map((requirement) => {
    const material = state.materials
      .filter((item) => item.projectId === projectId
        && item.requirementId === requirement.requirementId
        && item.currentVersionId === item.materialId)
      .sort((a, b) => b.versionNo - a.versionNo)[0]

    // 从 material_review 获取最新审核记录
    const latestReview = material?.materialId != null
      ? state.materialReviews
          .filter((r) => r.materialVersionId === material.currentVersionId)
          .sort((a, b) => b.reviewId - a.reviewId)[0]
      : null

    return {
      materialId: material?.materialId ?? null,
      requirementId: requirement.requirementId,
      requirementName: requirement.requirementName,
      requiredFlag: requirement.isRequired,
      description: requirement.description,
      submitStatus: material?.submitStatus ?? 'pending',
      currentVersionId: material?.currentVersionId ?? null,
      uploaded: material?.currentVersionId != null && material?.fileId != null,
      requirementSatisfied: requirement.isRequired !== 1
        || (material?.currentVersionId != null && material?.fileId != null),
      fileHash: material?.fileHash ?? null,
      fileId: material?.fileId ?? null,
      fileName: material?.fileName ?? '',
      versionNo: material?.versionNo ?? 1,
      remark: material?.remark ?? '等待上传',
      submittedAt: material?.submittedAt ?? null,
      reviewStatus: latestReview?.decision ?? null,
      reviewComment: latestReview?.comment ?? null,
      reviewedByName: latestReview ? getUserById(latestReview.reviewerId)?.realName ?? '' : null,
      reviewedAt: latestReview?.createdAt ?? null,
    }
  })
}

function buildProjectProgress(projectId) {
  const project = getProjectById(projectId)
  if (!project) {
    return null
  }

  const materials = buildMaterialViews(projectId).filter((item) => item.requiredFlag === 1)
  const requiredTotal = materials.length
  const submittedMaterials = materials.filter((item) => item.currentVersionId != null)
  const missingMaterials = materials
    .filter((item) => item.currentVersionId == null)
    .map((item) => item.requirementName)
  const submittedTotal = submittedMaterials.length
  const missingTotal = missingMaterials.length
  const completionRate = requiredTotal ? Number(((submittedTotal / requiredTotal) * 100).toFixed(2)) : 0

  // 材料是否齐全（派生属性，不修改项目状态）
  const materialComplete = requiredTotal === 0 || submittedTotal === requiredTotal

  // 只更新完成率，不修改项目生命周期状态
  project.completionRate = completionRate

  return {
    projectId,
    projectName: project.projectName,
    status: project.status, // 保持原有项目状态
    deadline: project.deadline,
    requiredTotal,
    submittedTotal,
    missingTotal,
    completionRate,
    missingMaterials,
    materialComplete,
    completenessLabel: materialComplete ? '材料已齐' : '材料未齐',
  }
}

function buildProjectDetail(projectId) {
  const project = getProjectById(projectId)
  if (!project) {
    return null
  }

  const notice = getNoticeById(project.noticeId)
  const leader = getUserById(project.leaderId)

  return {
    projectId: project.projectId,
    noticeId: project.noticeId,
    noticeTitle: notice?.title ?? '',
    leaderId: project.leaderId,
    leaderName: leader?.realName ?? '',
    projectName: project.projectName,
    teamName: project.teamName,
    status: project.status,
    deadline: project.deadline,
    completionRate: project.completionRate,
    members: state.projectMembers
      .filter((item) => item.projectId === projectId)
      .map((member) => ({
        memberId: member.memberId,
        userId: member.userId,
        realName: getUserById(member.userId)?.realName ?? '',
        memberRole: member.memberRole,
      })),
    materials: buildMaterialViews(projectId),
    reviewRecords: state.reviewRecords
      .filter((item) => item.projectId === projectId)
      .sort((left, right) => right.reviewId - left.reviewId),
  }
}

function buildLatestAiCheck(projectId) {
  if (!projectId) {
    return null
  }

  const progress = buildProjectProgress(projectId)
  const latestAiReview = state.reviewRecords
    .filter((item) => item.projectId === projectId && item.reviewType === 'ai')
    .sort((left, right) => right.reviewId - left.reviewId)[0]

  if (!latestAiReview) {
    return null
  }

  return {
    projectId,
    projectName: getProjectById(projectId)?.projectName ?? '',
    reviewResult: latestAiReview.reviewResult,
    reviewComment: latestAiReview.reviewComment,
    completionRate: progress?.completionRate ?? 0,
    missingMaterials: progress?.missingMaterials ?? [],
  }
}

function ensureRequirements(notice) {
  const existing = getNoticeRequirements(notice.noticeId)
  if (existing.length) {
    return existing
  }

  const rawContext = `${notice.title} ${notice.rawText}`.toLowerCase()
  const generated = [...BASE_REQUIREMENTS]

  if (rawContext.includes('ppt') || rawContext.includes('路演') || rawContext.includes('答辩')) {
    generated.push({
      requirementName: '路演答辩PPT',
      description: '用于路演或答辩展示的汇报材料。',
    })
  }

  generated.forEach((item, index) => {
    state.requirements.push({
      requirementId: nextId('requirementId'),
      noticeId: notice.noticeId,
      requirementName: item.requirementName,
      isRequired: 1,
      description: item.description,
      sortNo: index + 1,
      status: 'ACTIVE',
    })
  })

  return getNoticeRequirements(notice.noticeId)
}

export function getDashboardBootstrap(userId) {
  // Find the user's latest project via project_member records
  let latestProject = null
  if (userId) {
    const userProjectIds = state.projectMembers
      .filter((pm) => pm.userId === userId)
      .map((pm) => pm.projectId)
    const uniqueIds = [...new Set(userProjectIds)]
    const userProjects = state.projects.filter((p) => uniqueIds.includes(p.projectId))
    latestProject = userProjects.length
      ? userProjects.reduce((a, b) => (a.projectId > b.projectId ? a : b))
      : null
  }

  const currentUser = state.users.find((item) => item.userId === userId)
  const visibleNotices = currentUser?.role === 'student'
    ? state.notices.filter((item) => item.publishStatus === 'PUBLISHED')
    : state.notices
  const projectNotice = latestProject ? getNoticeById(latestProject.noticeId) : null
  const latestNotice = projectNotice
    || visibleNotices[visibleNotices.length - 1]
    || null

  return delayResponse({
    notice: latestNotice ? buildNoticeView(latestNotice) : null,
    noticeOptions: visibleNotices
      .slice()
      .reverse()
      .map((item) => ({
        value: item.noticeId,
        label: item.title,
        deadline: item.deadline,
      })),
    userOptions: state.users
      .filter((item) => item.role !== 'admin')
      .map((item) => ({
        value: item.userId,
        label: `${item.realName} · ${item.role === 'teacher' ? '指导教师' : '学生'}`,
        role: item.role,
      })),
    projectDetail: latestProject ? buildProjectDetail(latestProject.projectId) : null,
    progress: latestProject ? buildProjectProgress(latestProject.projectId) : null,
    aiCheck: latestProject ? buildLatestAiCheck(latestProject.projectId) : null,
  }, 'bootstrap success', 180)
}

export function uploadNotice(payload) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可上传通知', 403)
  const fileId = payload.file ? nextId('fileId') : null
  const fileName = payload.file?.name ?? payload.fileName ?? ''
  const resolvedTitle = payload.title
    || (fileName.includes('.') ? fileName.slice(0, fileName.lastIndexOf('.')) : fileName)
    || '未命名竞赛通知'

  if (fileId) {
    state.fileAssets.push({
      fileId,
      bizType: 'notice',
      fileName,
    })
  }

  const notice = {
    noticeId: nextId('noticeId'),
    title: resolvedTitle,
    organizer: payload.organizer || '教务处',
    deadline: payload.deadline || new Date(new Date().setDate(new Date().getDate() + 30)).toISOString(),
    targetGroup: payload.targetGroup || '校级创新创业团队',
    rawText: payload.rawText || `系统已接收通知文件 ${fileName || resolvedTitle}，等待执行智能解析。`,
    aiSummary: '通知内容已保存，等待执行智能解析。',
    noticeFileId: fileId,
    noticeType: 'COMPETITION',
    parseStatus: 'DRAFT',
    publishStatus: 'DRAFT',
    confirmedBy: null,
    confirmedAt: null,
    publishedAt: null,
    createdAt: new Date().toISOString(),
  }

  state.notices.push(notice)

  return delayResponse(
    {
      noticeId: notice.noticeId,
      fileId,
      title: notice.title,
    },
    '通知保存成功',
  )
}

export function replaceNoticeAttachment(noticeId, file) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可替换通知附件', 403)
  const notice = getNoticeById(noticeId)
  if (!notice) return rejectResponse('通知不存在', 404)
  if (notice.publishStatus !== 'DRAFT' || notice.confirmedAt || !['DRAFT', 'FAILED'].includes(notice.parseStatus)) {
    return rejectResponse('当前通知状态不允许替换附件', 409)
  }
  if (!file?.size) return rejectResponse('请选择非空通知附件', 400)
  const fileId = nextId('fileId')
  state.fileAssets.push({ fileId, bizType: 'notice', fileName: file.name })
  notice.noticeFileId = fileId
  notice.rawText = null
  notice.aiSummary = '待解析'
  return delayResponse({ noticeId, fileId, title: notice.title }, '通知附件已替换')
}

const noticeParseTasks = new Map()

export function getNoticeParseTask(taskId) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可查看解析任务', 403)
  const task = noticeParseTasks.get(Number(taskId))
  return task ? delayResponse(task, '任务查询成功', 80) : rejectResponse('解析任务不存在', 404)
}

export function getLatestNoticeParseTask(noticeId) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可查看解析任务', 403)
  const task = [...noticeParseTasks.values()].filter((item) => item.businessId === Number(noticeId)).at(-1) ?? null
  return delayResponse(task, '任务查询成功', 80)
}

export function parseNotice(noticeId) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可解析通知', 403)
  const notice = getNoticeById(noticeId)
  if (!notice) return rejectResponse('通知不存在', 404)
  if (!['DRAFT', 'FAILED', 'PARSED'].includes(notice.parseStatus)) {
    return rejectResponse('通知当前解析状态不允许重新解析', 409)
  }
  const materials = BASE_REQUIREMENTS.map((item) => ({
    name: item.requirementName,
    description: item.description,
    isRequired: true,
  }))
  const draft = {
    id: nextId('parseDraftId'),
    noticeId,
    aiTitle: notice.title,
    aiOrganizer: notice.organizer,
    aiDeadline: notice.deadline,
    aiTargetGroup: notice.targetGroup,
    aiKeyPoints: `AI 从通知文本中识别出 ${materials.length} 项材料要求，等待管理员核对。`,
    materials,
    rawAiResponse: JSON.stringify({ title: notice.title, materials }),
    status: 'PENDING',
    createdAt: new Date().toISOString(),
  }
  parseDrafts.set(noticeId, draft)
  notice.parseStatus = 'PARSING'
  notice.confirmedBy = null
  notice.confirmedAt = null

  const taskId = nextId('noticeTaskId')
  const task = {
    taskId, businessType: 'NOTICE_PARSE', businessId: noticeId,
    status: 'PENDING', resultOrigin: 'NONE', attemptNo: 1,
    createdAt: new Date().toISOString(), startedAt: null, finishedAt: null, errorSummary: null,
  }
  noticeParseTasks.set(taskId, task)
  window.setTimeout(() => {
    notice.parseStatus = 'PARSED'
    task.status = 'SUCCESS'
    task.resultOrigin = 'MODEL'
    task.startedAt = task.createdAt
    task.finishedAt = new Date().toISOString()
  }, 600)
  return delayResponse({ taskId, noticeId, status: 'PENDING' }, 'AI解析任务已接受', 80)
}

export function createProject(payload) {
  const actor = getAuthState().user
  if (!['student', 'admin'].includes(actor?.role)) return rejectResponse('只有学生或管理员可以创建项目', 403)
  if (actor.role !== 'admin' && payload.leaderId !== actor.userId) return rejectResponse('项目负责人必须为当前用户', 403)
  const notice = getNoticeById(payload.noticeId)
  if (!notice || notice.publishStatus !== 'PUBLISHED') return rejectResponse('只能申报已发布的通知', 400)
  if (!payload.projectName?.trim()) return rejectResponse('请填写项目名称', 400)
  if (payload.advisorId && getUserById(payload.advisorId)?.role !== 'teacher') return rejectResponse('指导教师必须是教师用户', 400)
  const requirements = ensureRequirements(notice)
  const deadline = payload.deadline || notice.deadline

  const project = {
    projectId: nextId('projectId'),
    noticeId: payload.noticeId,
    leaderId: payload.leaderId,
    projectName: payload.projectName,
    teamName: payload.teamName || '',
    status: 'DRAFT',
    deadline,
    completionRate: 0,
  }

  state.projects.push(project)

  state.projectMembers.push({
    memberId: nextId('memberId'),
    projectId: project.projectId,
    userId: payload.leaderId,
    memberRole: 'leader',
  })

  if (payload.advisorId) {
    state.projectMembers.push({
      memberId: nextId('memberId'),
      projectId: project.projectId,
      userId: payload.advisorId,
      memberRole: 'advisor',
    })
  }

  if (Array.isArray(payload.memberUserIds)) {
    payload.memberUserIds
      .filter((userId) => ![payload.leaderId, payload.advisorId].includes(userId))
      .forEach((userId) => {
        state.projectMembers.push({
          memberId: nextId('memberId'),
          projectId: project.projectId,
          userId,
          memberRole: 'member',
        })
      })
  }

  requirements.forEach((requirement) => {
    state.materials.push({
      materialId: nextId('materialId'),
      projectId: project.projectId,
      requirementId: requirement.requirementId,
      fileId: null,
      fileName: '',
      submitStatus: 'pending',
      currentVersionId: null,
      fileHash: null,
      versionNo: 0,
      remark: '系统已初始化材料条目，请上传对应申报材料。',
      submittedAt: null,
    })
  })

  buildProjectProgress(project.projectId)

  return delayResponse(
    {
      projectId: project.projectId,
      projectName: project.projectName,
      status: project.status,
      completionRate: project.completionRate,
      initializedMaterialCount: requirements.length,
    },
    '项目创建成功',
  )
}

export function getProjectDetail(projectId) {
  if (!canMockReadProject(projectId)) return rejectResponse('无权访问该项目', 403)
  if (!getProjectById(projectId)) return rejectResponse('项目不存在', 404)
  return delayResponse(buildProjectDetail(projectId), '项目详情获取成功', 180)
}

function canMockReadProject(projectId) {
  const user = getAuthState().user
  if (user?.role === 'admin') return true
  if (!user) return false
  return state.projectMembers.some((member) => member.projectId === Number(projectId)
    && member.userId === user.userId && (user.role === 'student' || member.memberRole === 'advisor'))
}

function canMockTeacherReview(projectId) {
  const user = getAuthState().user
  return user?.role === 'admin' || (user?.role === 'teacher' && canMockReadProject(projectId))
}

function canMockLeadProject(projectId) {
  const user = getAuthState().user
  return user?.role === 'admin' || (user?.role === 'student'
    && getProjectById(projectId)?.leaderId === user.userId)
}

export function getProjectProgress(projectId) {
  if (!canMockReadProject(projectId)) return rejectResponse('无权访问该项目', 403)
  if (!getProjectById(projectId)) return rejectResponse('项目不存在', 404)
  return delayResponse(buildProjectProgress(projectId), '项目进度获取成功', 180)
}

export function uploadMaterial(payload) {
  if (!canMockReadProject(payload.projectId) || !['student', 'admin'].includes(getAuthState().user?.role)) return rejectResponse('无权上传该项目材料', 403)
  const project = getProjectById(payload.projectId)
  if (!project || !['DRAFT', 'REVISION_REQUIRED'].includes(project.status)) {
    return rejectResponse('当前项目状态不允许上传或替换材料', 409)
  }
  const requirement = getNoticeRequirements(project.noticeId)
    .find((item) => item.requirementId === payload.requirementId)
  if (!requirement) {
    return rejectResponse('材料要求不存在或已停用', 400)
  }

  const versions = state.materials.filter(
    (item) => item.projectId === payload.projectId && item.requirementId === payload.requirementId,
  )
  const nextVersionNo = versions.filter((item) => item.fileId)
    .reduce((max, item) => Math.max(max, item.versionNo || 0), 0) + 1

  const fileId = nextId('fileId')
  const fileName = payload.file?.name ?? `material-${fileId}.docx`

  state.fileAssets.push({
    fileId,
    bizType: 'material',
    fileName,
  })

  versions.forEach((item) => { item.currentVersionId = null })
  const materialId = nextId('materialId')
  const material = {
    materialId,
    projectId: payload.projectId,
    requirementId: payload.requirementId,
    fileId,
    fileName,
    submitStatus: 'submitted',
    currentVersionId: materialId,
    fileHash: null,
    versionNo: nextVersionNo,
    remark: payload.remark || `已上传 ${fileName}`,
    submittedAt: new Date().toISOString(),
  }
  state.materials.push(material)

  const progress = buildProjectProgress(payload.projectId)

  return delayResponse(
    {
      materialId: material.materialId,
      projectId: payload.projectId,
      requirementId: payload.requirementId,
      fileId,
      versionNo: material.versionNo,
      submitStatus: 'submitted',
      projectStatus: progress.status,
      completionRate: progress.completionRate,
    },
    '材料上传成功',
  )
}

export function runMaterialCheck(projectId) {
  if (!canMockReadProject(projectId) || !['student', 'admin'].includes(getAuthState().user?.role)) return rejectResponse('无权核验该项目材料', 403)
  const project = getProjectById(projectId)
  if (!project || !['DRAFT', 'REVISION_REQUIRED'].includes(project.status)) {
    return rejectResponse('当前项目状态不允许运行 AI 材料检查', 409)
  }
  const progress = buildProjectProgress(projectId)
  const missingMaterials = progress.missingMaterials
  const reviewResult = missingMaterials.length ? 'WARNING' : 'PASSED'
  const reviewComment = missingMaterials.length
    ? `当前仍缺少 ${missingMaterials.length} 项必交材料：${missingMaterials.join('、')}。`
    : '当前必交材料已全部提交，可进入下一步申报流程。'

  state.reviewRecords.unshift({
    reviewId: nextId('reviewId'),
    projectId,
    reviewType: 'ai',
    reviewResult,
    reviewComment,
    reviewerName: '智能核验引擎',
    createdAt: new Date().toISOString(),
  })

  state.projectAiChecks.unshift({
    id: nextId('aiCheckId'),
    projectId,
    result: reviewResult,
    issueSummary: reviewResult === 'WARNING' ? reviewComment : null,
    materialVersionIds: buildMaterialViews(projectId)
      .map((item) => item.currentVersionId)
      .filter(Boolean),
    createdAt: new Date().toISOString(),
  })

  return delayResponse(
    {
      projectId,
      projectName: project.projectName,
      reviewResult,
      reviewComment,
      completionRate: progress.completionRate,
      missingMaterials,
    },
    '核验完成',
    320,
  )
}

export function addProjectMember(projectId, payload) {
  if (!canMockLeadProject(projectId)) return rejectResponse('无权修改项目成员', 403)
  const project = getProjectById(projectId)
  if (!project) {
    return rejectResponse('项目不存在', 404)
  }
  if (project.status !== 'DRAFT') {
    return rejectResponse('项目提交后不能修改成员', 409)
  }

  const user = getUserById(payload.userId)
  if (!user) {
    return rejectResponse('用户不存在', 400)
  }

  // Check for duplicate
  const exists = state.projectMembers.some(
    (pm) => pm.projectId === projectId && pm.userId === payload.userId,
  )
  if (exists) {
    return rejectResponse('该用户已是项目成员', 409)
  }

  const member = {
    memberId: nextId('memberId'),
    projectId,
    userId: payload.userId,
    memberRole: payload.memberRole,
  }

  state.projectMembers.push(member)

  return delayResponse(null, '成员添加成功', 200)
}

export function removeProjectMember(projectId, memberId) {
  if (!canMockLeadProject(projectId)) return rejectResponse('无权修改项目成员', 403)
  const project = getProjectById(projectId)
  if (!project || project.status !== 'DRAFT') {
    return rejectResponse('项目提交后不能修改成员', 409)
  }
  const index = state.projectMembers.findIndex(
    (pm) => pm.memberId === memberId && pm.projectId === projectId,
  )
  if (index === -1) {
    return rejectResponse('项目成员记录不存在', 404)
  }

  const member = state.projectMembers[index]
  if (member.memberRole === 'leader') {
    return rejectResponse('项目负责人不可移除', 400)
  }

  state.projectMembers.splice(index, 1)

  return delayResponse(null, '成员移除成功', 200)
}

// ===== 新增：文件内容查看 =====

// getFileContent 现在通过 downloadFileBlob 工作，此处保留以兼容旧调用
export function getFileContent(fileId) {
  const fileAsset = state.fileAssets.find((f) => f.fileId === fileId)
  if (!fileAsset) {
    return rejectResponse('文件不存在', 404)
  }

  return delayResponse(
    {
      fileId: fileAsset.fileId,
      fileName: fileAsset.fileName,
      fileExt: fileAsset.fileExt || 'docx',
      fileSize: fileAsset.fileSize || 1024,
      downloadUrl: `#mock-download/${fileId}`,
    },
    '文件准备就绪',
    100,
  )
}

// ===== 管理员项目总览 =====

export function getAllProjects(params = {}) {
  let projects = state.projects.map((p) => {
    const memberRecords = state.projectMembers.filter((pm) => pm.projectId === p.projectId)
    const leaderMember = memberRecords.find((pm) => pm.memberRole === 'leader')
    const notice = getNoticeById(p.noticeId)
    const materials = buildMaterialViews(p.projectId)
    const submitted = materials.filter((m) => m.currentVersionId != null).length
    const reviewed = materials.filter((m) => m.reviewStatus).length

    return {
      projectId: p.projectId,
      projectName: p.projectName,
      teamName: p.teamName,
      status: p.status,
      completionRate: p.completionRate,
      deadline: p.deadline,
      createdAt: p.createdAt || '2026-05-01T09:00:00',
      leaderName: getUserById(leaderMember?.userId)?.realName || '',
      noticeTitle: notice?.title || '',
      memberNames: memberRecords.map(
        (pm) => `${getUserById(pm.userId)?.realName || ''}（${{ leader: '负责人', advisor: '指导教师', member: '成员' }[pm.memberRole]}）`,
      ),
      submittedCount: submitted,
      totalCount: materials.length,
      reviewedCount: reviewed,
    }
  })

  // keyword: 项目名 / 负责人 / 通知标题
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    projects = projects.filter(
      (p) =>
        p.projectName.toLowerCase().includes(kw) ||
        p.leaderName.toLowerCase().includes(kw) ||
        p.noticeTitle.toLowerCase().includes(kw),
    )
  }

  // 状态筛选
  if (params.status) {
    projects = projects.filter((p) => p.status === params.status)
  }

  // 通知筛选
  if (params.noticeId) {
    projects = projects.filter((p) => {
      const project = state.projects.find((sp) => sp.projectId === p.projectId)
      return project && project.noticeId === params.noticeId
    })
  }

  // 按创建时间倒序
  projects.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))

  // 分页
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  const total = projects.length
  const start = (pageNum - 1) * pageSize
  const records = projects.slice(start, start + pageSize)

  return delayResponse({
    records,
    total,
    pages: Math.ceil(total / pageSize),
    current: pageNum,
    size: pageSize,
  }, '项目列表获取成功', 200)
}

// ===== 新增：我的项目列表 =====

export function getMyProjects(params = {}) {
  const user = state.users.find((entry) => entry.userId === Number(params.userId))
  const visibleProjectIds = user
    ? new Set(state.projectMembers.filter((member) => member.userId === user.userId
      && (user.role !== 'teacher' || member.memberRole === 'advisor')).map((member) => member.projectId))
    : new Set()
  let projects = state.projects.filter((p) => visibleProjectIds.has(p.projectId)).map((p) => {
    const memberRecords = state.projectMembers.filter((pm) => pm.projectId === p.projectId)
    const leaderMember = memberRecords.find((pm) => pm.memberRole === 'leader')
    const notice = getNoticeById(p.noticeId)
    const materials = buildMaterialViews(p.projectId)
    const submitted = materials.filter((m) => m.currentVersionId != null).length
    const reviewed = materials.filter((m) => m.reviewStatus).length

    return {
      projectId: p.projectId,
      projectName: p.projectName,
      teamName: p.teamName,
      status: p.status,
      completionRate: p.completionRate,
      deadline: p.deadline,
      leaderName: getUserById(leaderMember?.userId)?.realName || '',
      noticeTitle: notice?.title || '',
      memberNames: memberRecords.map(
        (pm) => `${getUserById(pm.userId)?.realName || ''}（${{ leader: '负责人', advisor: '指导教师', member: '成员' }[pm.memberRole]}）`,
      ),
      submittedCount: submitted,
      totalCount: materials.length,
      reviewedCount: reviewed,
    }
  })

  // P1-4: filter by keyword
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    projects = projects.filter(
      (p) => p.projectName.toLowerCase().includes(kw) || p.noticeTitle.toLowerCase().includes(kw),
    )
  }
  // P1-4: filter by status
  if (params.status) {
    projects = projects.filter((p) => p.status === params.status)
  }
  if (params.deadlineBefore) {
    projects = projects.filter((p) => p.deadline && p.deadline <= params.deadlineBefore)
  }

  // P1-4: paginate
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  const total = projects.length
  const start = (pageNum - 1) * pageSize
  const records = projects.slice(start, start + pageSize)

  return delayResponse({
    records,
    total,
    pages: Math.ceil(total / pageSize),
    current: pageNum,
    size: pageSize,
  }, '项目列表获取成功', 200)
}

export function searchUserOptions({ role, keyword = '' }) {
  const normalizedKeyword = keyword.trim().toLowerCase()
  const records = state.users
    .filter((user) => user.role === role)
    .filter((user) => !normalizedKeyword
      || user.realName.toLowerCase().includes(normalizedKeyword)
      || user.username.toLowerCase().includes(normalizedKeyword))
    .slice(0, 20)
    .map((user) => ({
      value: user.userId,
      label: `${user.realName} · ${role === 'teacher' ? '指导教师' : '学生'}`,
      role: user.role,
    }))
  return delayResponse(records, '候选用户查询成功', 120)
}

export function getMaterialVersionHistory(projectId, requirementId) {
  if (!canMockReadProject(projectId)) return rejectResponse('无权查看材料版本', 403)
  const versions = state.materials
    .filter((item) => item.projectId === projectId
      && item.requirementId === requirementId
      && item.fileId != null)
    .sort((left, right) => right.versionNo - left.versionNo)
    .map((item) => {
      const review = state.materialReviews
        .filter((entry) => entry.materialVersionId === item.materialId)
        .sort((left, right) => right.reviewId - left.reviewId)[0]
      const file = getFileById(item.fileId)
      return {
        materialId: item.materialId,
        requirementId,
        requirementName: getRequirementName(requirementId),
        fileId: item.fileId,
        fileName: file?.fileName || item.fileName,
        fileExt: file?.fileExt || (item.fileName?.split('.').pop() ?? ''),
        fileSize: file?.fileSize || 1024,
        versionNo: item.versionNo,
        remark: item.remark,
        submittedAt: item.submittedAt,
        currentVersion: item.currentVersionId === item.materialId,
        reviewStatus: review?.decision ?? null,
        reviewComment: review?.comment ?? null,
        reviewedByName: review ? getUserById(review.reviewerId)?.realName ?? '' : null,
        reviewedAt: review?.createdAt ?? null,
      }
    })
  return delayResponse(versions, '材料版本历史查询成功', 140)
}

export function getProjectAiChecks(projectId) {
  if (!canMockReadProject(projectId)) return rejectResponse('无权查看 AI 检查记录', 403)
  const currentVersionIds = buildMaterialViews(projectId)
    .map((item) => item.currentVersionId)
    .filter(Boolean)
    .sort((left, right) => left - right)
  const records = state.projectAiChecks
    .filter((item) => item.projectId === projectId)
    .map((item) => {
      const snapshot = [...item.materialVersionIds].sort((left, right) => left - right)
      return {
        ...item,
        stale: JSON.stringify(snapshot) !== JSON.stringify(currentVersionIds),
        checkedAt: item.createdAt,
      }
    })
  return delayResponse(records, 'AI 检查历史查询成功', 140)
}

// ===== 新增：教师审核材料（append-only，匹配真实后端 material_review 表） =====

export function reviewMaterial(payload) {
  if (!canMockTeacherReview(payload.projectId)) return rejectResponse('无权审核该项目', 403)
  const material = state.materials.find((m) => m.materialId === payload.materialId)
  if (!material) {
    return rejectResponse('材料记录不存在', 404)
  }
  const project = getProjectById(payload.projectId)
  if (!project || project.status !== 'UNDER_REVIEW' || material.currentVersionId !== material.materialId) {
    return rejectResponse('只能审核人工审核中项目的当前材料版本', 409)
  }

  // P0-2: 后端从 JWT 获取审核人，Mock 模式模拟教师（userId=2）作为审核人
  const mockReviewerId = getAuthState().user?.userId
  const now = new Date().toISOString()
  const decision = payload.reviewStatus
  const comment = payload.reviewComment || ''

  // Append-only: 写入 material_review 记录（匹配真实后端行为）
  const reviewId = nextId('reviewId')
  state.materialReviews.push({
    reviewId,
    materialId: material.materialId,
    materialVersionId: material.materialId, // 当前版本 materialVersionId = materialId
    reviewerId: mockReviewerId,
    decision,
    comment,
    createdAt: now,
  })

  // 同步写入 review_record 用于审计（匹配真实后端 MaterialService 第 278-290 行）
  state.reviewRecords.push({
    reviewId: state.counters.reviewId++,
    projectId: payload.projectId,
    reviewType: 'teacher',
    reviewResult: decision,
    reviewComment: `材料「${getRequirementName(material.requirementId)}」审核结果：`
      + `${decision === 'APPROVED' ? '通过' : '需修改'}`
      + (comment ? ` —— ${comment}` : ''),
    reviewerName: getUserById(mockReviewerId)?.realName || '',
    createdAt: now,
  })

  // 退回修改时通知项目负责人
  if (decision === 'REVISION_REQUIRED') {
    const project = getProjectById(payload.projectId)
    if (project) {
      state.notifyMessages.push({
        msgId: nextId('notifyMsgId'),
        projectId: payload.projectId,
        receiverId: project.leaderId,
        msgType: 'material',
        msgContent: `材料「${getRequirementName(material.requirementId)}」已被教师退回修改`
          + (comment ? `，修改意见：${comment}` : ''),
        isRead: 0,
        createdAt: now,
      })
    }
  }

  return delayResponse(
    {
      materialId: material.materialId,
      reviewStatus: decision,
      reviewComment: comment,
      reviewedAt: now,
    },
    decision === 'APPROVED' ? '材料审核通过' : '已提交修改意见',
    200,
  )
}

// ===== 新增：项目级审核决定 =====

export function approveProject(projectId) {
  if (!canMockTeacherReview(projectId)) return rejectResponse('无权审核该项目', 403)
  const project = state.projects.find((p) => p.projectId === projectId)
  if (!project) {
    return rejectResponse('项目不存在', 404)
  }
  if (project.status !== 'UNDER_REVIEW') {
    return rejectResponse('项目当前状态不允许此操作', 409)
  }

  // 校验必交材料（匹配真实后端 ProjectService.approve() 业务校验）
  const materials = buildMaterialViews(projectId)
  const requiredMaterials = materials.filter((m) => m.requiredFlag === 1)
  const unsubmitted = requiredMaterials.filter((m) => m.currentVersionId == null)
  if (unsubmitted.length) {
    return rejectResponse(`仍有必交材料未提交，不能通过项目：${unsubmitted.map((m) => m.requirementName).join('、')}`, 409)
  }
  const unreviewedOrRejected = requiredMaterials.filter((m) => m.reviewStatus !== 'APPROVED')
  if (unreviewedOrRejected.length) {
    const details = unreviewedOrRejected.map(
      (m) => `${m.requirementName}（${m.reviewStatus == null ? '未审核' : '需修改'}）`,
    ).join('、')
    return rejectResponse(`仍有必交材料未审核或需要修改，不能通过项目：${details}`, 409)
  }

  project.status = 'APPROVED'
  const now = new Date().toISOString()
  state.reviewRecords.push({ reviewId: nextId('reviewId'), projectId, reviewType: 'teacher_project',
    reviewResult: 'APPROVED', reviewComment: '项目审核通过',
    reviewerId: getAuthState().user?.userId,
    reviewerName: getUserById(getAuthState().user?.userId)?.realName || '', createdAt: now })

  // 通知项目负责人
  state.notifyMessages.push({
    msgId: nextId('notifyMsgId'),
    projectId,
    receiverId: project.leaderId,
    msgType: 'project',
    msgContent: `您的项目「${project.projectName}」已审核通过！`,
    isRead: 0,
    createdAt: now,
  })

  return delayResponse(null, '项目已审核通过', 200)
}

export function requestRevision(projectId, reason) {
  if (!canMockTeacherReview(projectId)) return rejectResponse('无权审核该项目', 403)
  const project = state.projects.find((p) => p.projectId === projectId)
  if (!project) {
    return rejectResponse('项目不存在', 404)
  }
  if (project.status !== 'UNDER_REVIEW') {
    return rejectResponse('项目当前状态不允许此操作', 409)
  }
  if (!reason?.trim()) return rejectResponse('退回项目必须填写具体原因', 400)

  project.status = 'REVISION_REQUIRED'
  const safeReason = reason.trim()
  const now = new Date().toISOString()
  state.reviewRecords.push({ reviewId: nextId('reviewId'), projectId, reviewType: 'teacher_project',
    reviewResult: 'REVISION_REQUIRED', reviewComment: safeReason,
    reviewerId: getAuthState().user?.userId,
    reviewerName: getUserById(getAuthState().user?.userId)?.realName || '', createdAt: now })

  // 通知项目负责人
  state.notifyMessages.push({
    msgId: nextId('notifyMsgId'),
    projectId,
    receiverId: project.leaderId,
    msgType: 'project',
    msgContent: `您的项目「${project.projectName}」已被退回修改。审核意见：${safeReason}`,
    isRead: 0,
    createdAt: now,
  })

  return delayResponse(null, '项目已退回修改', 200)
}

export function submitProject(projectId) {
  if (!canMockLeadProject(projectId)) return rejectResponse('无权提交该项目', 403)
  const project = state.projects.find((p) => p.projectId === projectId)
  if (!project) {
    return rejectResponse('项目不存在', 404)
  }
  if (project.status !== 'DRAFT') {
    return rejectResponse('项目当前状态不允许提交审核', 409)
  }
  if (project.deadline && new Date(project.deadline).getTime() < Date.now()) {
    return rejectResponse('已超过申报截止时间，无法提交审核', 400)
  }
  // 检查所有必交材料均已提交
  const materials = buildMaterialViews(projectId)
  const requiredMaterials = materials.filter((m) => m.requiredFlag === 1)
  const unsubmitted = requiredMaterials.filter((m) => m.currentVersionId == null)
  if (unsubmitted.length) {
    return rejectResponse('材料未齐全，无法提交审核。缺失材料：' + unsubmitted.map((m) => m.requirementName).join('、'), 400)
  }

  project.status = 'UNDER_REVIEW'
  state.reviewRecords.push({ reviewId: nextId('reviewId'), projectId, reviewType: 'lifecycle',
    reviewResult: 'UNDER_REVIEW', reviewComment: '项目首次提交审核',
    reviewerName: getUserById(getAuthState().user?.userId)?.realName || '', createdAt: new Date().toISOString() })
  return delayResponse(null, '项目已提交审核', 200)
}

export function resubmitProject(projectId) {
  if (!canMockLeadProject(projectId)) return rejectResponse('无权重新提交该项目', 403)
  const project = state.projects.find((p) => p.projectId === projectId)
  if (!project) {
    return rejectResponse('项目不存在', 404)
  }
  if (project.status !== 'REVISION_REQUIRED') {
    return rejectResponse('项目当前状态不允许重新提交', 409)
  }
  if (project.deadline && new Date(project.deadline).getTime() < Date.now()) {
    return rejectResponse('已超过申报截止时间，无法重新提交审核', 400)
  }
  // 检查所有必交材料均已提交
  const materials = buildMaterialViews(projectId)
  const requiredMaterials = materials.filter((m) => m.requiredFlag === 1)
  const unsubmitted = requiredMaterials.filter((m) => m.currentVersionId == null)
  if (unsubmitted.length) {
    return rejectResponse('材料未齐全，无法重新提交。缺失材料：' + unsubmitted.map((m) => m.requirementName).join('、'), 400)
  }

  project.status = 'UNDER_REVIEW'
  state.reviewRecords.push({ reviewId: nextId('reviewId'), projectId, reviewType: 'lifecycle',
    reviewResult: 'UNDER_REVIEW', reviewComment: '项目修改后重新提交审核',
    reviewerName: getUserById(getAuthState().user?.userId)?.realName || '', createdAt: new Date().toISOString() })
  return delayResponse(null, '项目已重新提交审核', 200)
}

// ===== 新增：项目审核状态 =====

export function getProjectReviewStatus(projectId) {
  if (!canMockTeacherReview(projectId)) return rejectResponse('无权查看项目审核状态', 403)
  // buildMaterialViews 已从 materialReviews 注入审核字段
  const materials = buildMaterialViews(projectId)
  return delayResponse(materials, '审核状态获取成功', 200)
}

export function getMyReviewHistory(params = {}) {
  const reviewerId = Number(params.userId)
  const user = getUserById(reviewerId)
  if (!user || !['teacher', 'admin'].includes(user.role)) return rejectResponse('只有教师或管理员可以查看审核记录', 403)
  const ownProjectIds = new Set(state.projectMembers.filter((member) =>
    member.userId === reviewerId && member.memberRole === 'advisor').map((member) => member.projectId))
  const materialEntries = state.materialReviews.filter((entry) => entry.reviewerId === reviewerId)
    .map((entry) => {
      const material = state.materials.find((item) => item.materialId === entry.materialVersionId)
      return material && {
        reviewId: entry.reviewId, projectId: material.projectId,
        projectName: getProjectById(material.projectId)?.projectName,
        requirementName: getRequirementName(material.requirementId), versionNo: material.versionNo,
        decision: entry.decision, comment: entry.comment, reviewerName: user.realName,
        createdAt: entry.createdAt, reviewType: 'material',
      }
    }).filter(Boolean)
  const projectEntries = state.reviewRecords.filter((entry) => entry.reviewType === 'teacher_project'
    && entry.reviewerId === reviewerId).map((entry) => ({
    reviewId: entry.reviewId, projectId: entry.projectId,
    projectName: getProjectById(entry.projectId)?.projectName, requirementName: null, versionNo: null,
    decision: entry.reviewResult, comment: entry.reviewComment, reviewerName: entry.reviewerName,
    createdAt: entry.createdAt, reviewType: 'project',
  }))
  const all = [...materialEntries, ...projectEntries]
    .filter((entry) => user.role === 'admin' || ownProjectIds.has(entry.projectId))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.reviewId - a.reviewId)
  const pageNum = Number(params.pageNum) || 1
  const pageSize = Number(params.pageSize) || 10
  return delayResponse({ records: all.slice((pageNum - 1) * pageSize, pageNum * pageSize),
    total: all.length, pages: Math.ceil(all.length / pageSize), current: pageNum, size: pageSize }, '审核记录查询成功', 140)
}

// ===== 新增：材料审核记录（append-only，来自 material_review 表） =====

function initMaterialReviews() {
  return []
}

// ===== 新增：审计日志 =====

function initAgentTaskLogs() {
  return [
    {
      taskId: 1,
      projectId: 1,
      toolName: 'checkMaterialTool',
      inputSummary: '审核项目材料: 基于大模型的校园竞赛申报材料智能核验助手, 已提交=1/3, 可审核文件=1',
      resultSummary: '【材料缺失】以下材料尚未提交：团队成员信息表、指导教师意见表。\n【内容审核】系统检查通过：所有必交材料已提交。当前无可审核的文件内容。',
      executeStatus: 'SUCCESS',
      createdAt: '2026-05-01T11:20:00',
    },
    {
      taskId: 2,
      projectId: 1,
      toolName: 'parseNoticeTool',
      inputSummary: '解析通知: 大学生创新创业训练计划项目申报通知 (152 字符)',
      resultSummary: '【AI解析】主办方：创新创业学院；截止时间：2026-06-15 23:59；面向对象：全日制本科生团队；关键内容：围绕创新创业训练计划项目开展申报；共识别 3 项材料要求：项目申报书、团队成员信息表、指导教师意见表。',
      executeStatus: 'SUCCESS',
      createdAt: '2026-04-28T09:15:00',
    },
    {
      taskId: 3,
      projectId: null,
      toolName: 'parseNoticeTool',
      inputSummary: '解析通知: 2026年"挑战杯"大学生创业计划竞赛通知 (210 字符)',
      resultSummary: '【AI解析】主办方：校团委；截止时间：2026-07-10 17:00；面向对象：全校本科生及研究生团队...',
      executeStatus: 'SUCCESS',
      createdAt: '2026-04-20T14:30:00',
    },
    {
      taskId: 4,
      projectId: 2,
      toolName: 'checkMaterialTool',
      inputSummary: '审核项目材料: 智能校园导航系统, 已提交=2/3, 可审核文件=2',
      resultSummary: '【材料缺失】以下材料尚未提交：团队成员信息表。\n【内容审核】项目申报书内容完整，路演PPT结构清晰。',
      executeStatus: 'SUCCESS',
      createdAt: '2026-06-10T16:45:00',
    },
    {
      taskId: 5,
      projectId: 3,
      toolName: 'parseNoticeTool',
      inputSummary: '解析通知: 基于深度学习的课堂专注度分析平台 (89 字符)',
      resultSummary: '【AI解析】通知内容较短，未能提取完整结构化信息，建议人工补充材料要求。',
      executeStatus: 'WARNING',
      createdAt: '2026-05-15T10:00:00',
    },
    {
      taskId: 6,
      projectId: 4,
      toolName: 'checkMaterialTool',
      inputSummary: '审核项目材料: 校园二手书交易小程序, 已提交=3/3, 可审核文件=3',
      resultSummary: '【全部通过】3项必交材料均已提交并审核通过：项目申报书、团队成员信息表、指导教师意见表。',
      executeStatus: 'SUCCESS',
      createdAt: '2026-06-20T09:30:00',
    },
    {
      taskId: 7,
      projectId: 5,
      toolName: 'checkMaterialTool',
      inputSummary: '审核项目材料: 基于区块链的学分认证系统, 已提交=1/2, 可审核文件=1',
      resultSummary: '【材料缺失】以下材料尚未提交：指导教师意见表。\n【内容审核】项目申报书内容基本完整，建议补充技术方案细节。',
      executeStatus: 'WARNING',
      createdAt: '2026-06-25T14:00:00',
    },
    {
      taskId: 8,
      projectId: 2,
      toolName: 'parseNoticeTool',
      inputSummary: '重新解析通知: 大学生创新创业训练计划项目申报通知 (补充材料后)',
      resultSummary: '【AI解析】识别到新增加材料需求：路演答辩PPT。已追加到材料清单。',
      executeStatus: 'SUCCESS',
      createdAt: '2026-06-12T11:00:00',
    },
    {
      taskId: 9,
      projectId: 4,
      toolName: 'parseNoticeTool',
      inputSummary: '解析通知: 校园二手书交易小程序申报 (73 字符)',
      resultSummary: '【AI解析】主办方：校创业指导中心；截止时间：2026-07-01 23:59；面向对象：全校本科生。',
      executeStatus: 'SUCCESS',
      createdAt: '2026-05-20T08:00:00',
    },
    {
      taskId: 10,
      projectId: 5,
      toolName: 'parseNoticeTool',
      inputSummary: '解析通知: 区块链学分认证系统项目申报 (95 字符)',
      resultSummary: '【AI解析】主办方：计算机学院；截止时间：2026-06-30 23:59；面向对象：计算机相关专业学生。',
      executeStatus: 'FAILED',
      createdAt: '2026-05-18T15:30:00',
    },
    {
      taskId: 11,
      projectId: 1,
      toolName: 'checkMaterialTool',
      inputSummary: '二次审核: 基于大模型的校园竞赛申报材料智能核验助手, 已提交=2/3',
      resultSummary: '【材料缺失】指导教师意见表仍未提交。\n【内容审核】团队成员信息表已更新，内容合格。',
      executeStatus: 'SUCCESS',
      createdAt: '2026-05-10T13:20:00',
    },
    {
      taskId: 12,
      projectId: 2,
      toolName: 'checkMaterialTool',
      inputSummary: '终审核: 智能校园导航系统, 已提交=3/3',
      resultSummary: '【全部通过】所有材料已提交，内容审核通过，建议进入教师审核阶段。',
      executeStatus: 'SUCCESS',
      createdAt: '2026-06-15T10:00:00',
    },
  ]
}

export function getAgentTaskLogs(params = {}) {
  let logs = state.agentTaskLogs.slice()

  if (params.projectId) {
    logs = logs.filter((l) => l.projectId === params.projectId)
  }
  if (params.toolName) {
    logs = logs.filter((l) => l.toolName === params.toolName)
  }
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    logs = logs.filter(
      (l) =>
        (l.inputSummary || '').toLowerCase().includes(kw) ||
        (l.resultSummary || '').toLowerCase().includes(kw),
    )
  }

  logs.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))

  // P1-4: paginate
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  const total = logs.length
  const start = (pageNum - 1) * pageSize
  const records = logs.slice(start, start + pageSize)

  return delayResponse({
    records,
    total,
    pages: Math.ceil(total / pageSize),
    current: pageNum,
    size: pageSize,
  }, '审计日志查询成功', 220)
}

// ===== 新增：消息中心 =====

function initNotifyMessages() {
  return [
    {
      msgId: 1,
      projectId: 1,
      receiverId: 3,
      msgType: 'material',
      msgContent: "项目 '基于大模型的校园竞赛申报材料智能核验助手' 材料检查结果：WARNING。请查看审核意见并及时处理。",
      isRead: 0,
      createdAt: '2026-05-01T11:20:00',
    },
    {
      msgId: 2,
      projectId: 1,
      receiverId: 3,
      msgType: 'material',
      msgContent: "材料「项目申报书」已被教师退回修改，修改意见：请补充项目的技术创新点详细描述，当前版本过于简略。",
      isRead: 0,
      createdAt: '2026-05-03T08:30:00',
    },
    {
      msgId: 3,
      projectId: 1,
      receiverId: 3,
      msgType: 'deadline',
      msgContent: '项目「基于大模型的校园竞赛申报材料智能核验助手」截止日期为 2026-06-15，距离截止仅剩 3 天，请尽快完善材料。',
      isRead: 1,
      createdAt: '2026-06-12T10:00:00',
    },
    {
      msgId: 4,
      projectId: 1,
      receiverId: 3,
      msgType: 'system',
      msgContent: '系统通知：2026年大学生创新创业训练计划项目申报已开放，请登录系统查看最新通知并进行项目申报。',
      isRead: 1,
      createdAt: '2026-04-10T09:00:00',
    },
    {
      msgId: 5,
      projectId: 2,
      receiverId: 3,
      msgType: 'material',
      msgContent: "材料「路演答辩PPT」已被教师退回修改，修改意见：PPT页数过多（当前38页），建议精简至20页以内，突出核心技术方案。",
      isRead: 0,
      createdAt: '2026-06-15T14:20:00',
    },
    {
      msgId: 6,
      projectId: null,
      receiverId: 3,
      msgType: 'system',
      msgContent: '欢迎使用校园竞赛申报材料智能核验系统！本系统将辅助您高效完成竞赛项目申报材料的准备与核验工作。',
      isRead: 0,
      createdAt: '2026-04-01T08:00:00',
    },
    {
      msgId: 7,
      projectId: 2,
      receiverId: 4,
      msgType: 'material',
      msgContent: "项目「智能校园导航系统」材料「项目申报书」审核通过，请继续提交剩余材料。",
      isRead: 0,
      createdAt: '2026-06-11T09:00:00',
    },
    {
      msgId: 8,
      projectId: 2,
      receiverId: 4,
      msgType: 'deadline',
      msgContent: '项目「智能校园导航系统」截止日期为 2026-06-15，距离截止仅剩 4 天，请尽快完善材料。',
      isRead: 0,
      createdAt: '2026-06-11T09:05:00',
    },
    {
      msgId: 9,
      projectId: 3,
      receiverId: 5,
      msgType: 'system',
      msgContent: '项目「基于深度学习的课堂专注度分析平台」已创建成功，请根据材料清单准备申报材料。',
      isRead: 1,
      createdAt: '2026-05-15T10:05:00',
    },
    {
      msgId: 10,
      projectId: 4,
      receiverId: 6,
      msgType: 'material',
      msgContent: '恭喜！项目「校园二手书交易小程序」所有材料审核通过，项目已被批准立项。',
      isRead: 0,
      createdAt: '2026-06-20T10:00:00',
    },
    {
      msgId: 11,
      projectId: 5,
      receiverId: 7,
      msgType: 'material',
      msgContent: "项目「基于区块链的学分认证系统」材料「项目申报书」已被教师退回修改，修改意见：请补充区块链技术方案的安全性与可行性分析。",
      isRead: 0,
      createdAt: '2026-06-28T11:00:00',
    },
    {
      msgId: 12,
      projectId: 5,
      receiverId: 7,
      msgType: 'deadline',
      msgContent: '项目「基于区块链的学分认证系统」截止日期为 2026-06-30，距离截止仅剩 2 天，请尽快补充修改材料。',
      isRead: 0,
      createdAt: '2026-06-28T11:30:00',
    },
  ]
}

function canMockAccessMessages(userId) {
  const actor = getAuthState().user
  return Boolean(userId && actor && (actor.userId === userId || actor.role === 'admin'))
}

export function getNotifyMessages(userId, params = {}) {
  if (!canMockAccessMessages(userId)) return rejectResponse('无权查看其他用户的消息', 403)
  const isRead = params.isRead
  let messages = state.notifyMessages
    .filter((m) => m.receiverId === userId)
    .slice()

  if (isRead !== undefined && isRead !== null) {
    messages = messages.filter((m) => m.isRead === isRead)
  }
  // P1-4: keyword search
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    messages = messages.filter((m) => m.msgContent.toLowerCase().includes(kw))
  }

  messages.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))

  // P1-4: paginate
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  const total = messages.length
  const start = (pageNum - 1) * pageSize
  const records = messages.slice(start, start + pageSize)

  return delayResponse({
    records,
    total,
    pages: Math.ceil(total / pageSize),
    current: pageNum,
    size: pageSize,
  }, '消息列表查询成功', 200)
}

export function getUnreadCount(userId) {
  if (!canMockAccessMessages(userId)) return rejectResponse('无权查看其他用户的消息', 403)
  const count = state.notifyMessages.filter(
    (m) => m.receiverId === userId && m.isRead === 0,
  ).length
  return delayResponse(count, '未读消息数查询成功', 100)
}

export function markMessageRead(msgId) {
  const msg = state.notifyMessages.find((m) => m.msgId === msgId)
  if (!msg) return rejectResponse('消息不存在', 404)
  if (!canMockAccessMessages(msg.receiverId)) return rejectResponse('无权操作其他用户的消息', 403)
  msg.isRead = 1
  return delayResponse(null, '消息已标记为已读', 120)
}

export function markAllMessagesRead(userId) {
  if (!canMockAccessMessages(userId)) return rejectResponse('无权操作其他用户的消息', 403)
  state.notifyMessages
    .filter((m) => m.receiverId === userId && m.isRead === 0)
    .forEach((m) => { m.isRead = 1 })
  return delayResponse(null, '全部消息已标记为已读', 150)
}

// ===== P1-1: 解析草稿管理（Mock） =====

const parseDrafts = new Map()

function initParseDrafts() {
  parseDrafts.clear()
}

initParseDrafts()

export function getParseDraft(noticeId) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可查看解析草稿', 403)
  const draft = parseDrafts.get(noticeId)
  if (!draft || draft.status !== 'PENDING') {
    return rejectResponse('该通知没有待确认的解析草稿', 400)
  }
  return delayResponse(draft, '草稿获取成功', 150)
}

export function updateParseDraft(noticeId, payload) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可修改解析草稿', 403)
  const draft = parseDrafts.get(noticeId)
  if (!draft || draft.status !== 'PENDING') {
    return rejectResponse('该通知没有待确认的解析草稿，或草稿已确认', 400)
  }
  Object.assign(draft, {
    aiTitle: payload.aiTitle,
    aiOrganizer: payload.aiOrganizer,
    aiDeadline: payload.aiDeadline,
    aiTargetGroup: payload.aiTargetGroup,
    aiKeyPoints: payload.aiKeyPoints,
    materials: payload.materials,
  })
  return delayResponse(draft, '草稿已更新', 200)
}

export function confirmParse(noticeId) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可确认解析草稿', 403)
  const draft = parseDrafts.get(noticeId)
  const notice = state.notices.find((item) => item.noticeId === noticeId)
  if (!notice) {
    return rejectResponse('通知不存在', 404)
  }
  if (notice.parseStatus !== 'PARSED' || !draft || draft.status !== 'PENDING') {
    return rejectResponse('该通知没有可确认的解析草稿', 409)
  }
  if (!draft.materials?.length) {
    return rejectResponse('材料列表不能为空', 400)
  }
  draft.status = 'CONFIRMED'

  // 更新通知数据
  notice.title = draft.aiTitle || notice.title
  notice.organizer = draft.aiOrganizer || notice.organizer
  notice.deadline = draft.aiDeadline || notice.deadline
  notice.targetGroup = draft.aiTargetGroup || notice.targetGroup
  notice.aiSummary = `【AI解析】主办方：${draft.aiOrganizer || '未知'}；共识别 ${draft.materials.length} 项材料要求。`
  notice.confirmedBy = 1
  notice.confirmedAt = new Date().toISOString()
  state.requirements = state.requirements.filter((item) => item.noticeId !== noticeId)
  draft.materials.forEach((material, index) => {
    state.requirements.push({
      requirementId: nextId('requirementId'),
      noticeId,
      requirementName: material.name,
      description: material.description,
      isRequired: material.isRequired ? 1 : 0,
      sortNo: index + 1,
      status: 'ACTIVE',
      source: 'AI',
    })
  })

  return delayResponse(null, '解析结果已确认，通知数据已更新', 300)
}

// ===== 通知管理（Mock） =====

export function publishNotice(noticeId) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可发布通知', 403)
  const notice = state.notices.find((n) => n.noticeId === noticeId)
  if (!notice) {
    return rejectResponse('通知不存在', 404)
  }
  if (notice.publishStatus !== 'DRAFT') {
    return rejectResponse('通知当前发布状态不允许发布', 409)
  }
  if (notice.parseStatus !== 'PARSED' || !notice.confirmedAt) {
    return rejectResponse('通知解析结果尚未经管理员确认，无法发布', 400)
  }
  if (!getNoticeRequirements(noticeId).length) {
    return rejectResponse('通知无材料清单，无法发布', 400)
  }
  notice.publishStatus = 'PUBLISHED'
  notice.publishedAt = new Date().toISOString()
  return delayResponse(null, '通知已发布', 200)
}

export function archiveNotice(noticeId) {
  if (getAuthState().user?.role !== 'admin') return rejectResponse('仅管理员可归档通知', 403)
  const notice = state.notices.find((n) => n.noticeId === noticeId)
  if (!notice) {
    return rejectResponse('通知不存在', 404)
  }
  if (notice.publishStatus !== 'PUBLISHED') {
    return rejectResponse('只有已发布通知可以归档', 409)
  }
  notice.publishStatus = 'ARCHIVED'
  return delayResponse(null, '通知已归档', 200)
}

export function listNotices(params = {}) {
  let notices = state.notices.slice()
  if (getAuthState().user?.role === 'student') {
    notices = notices.filter((notice) => notice.publishStatus === 'PUBLISHED')
  }
  if (params.keyword) {
    const kw = params.keyword.toLowerCase()
    notices = notices.filter(
      (n) => n.title.toLowerCase().includes(kw) || (n.organizer || '').toLowerCase().includes(kw),
    )
  }
  if (params.publishStatus) {
    notices = notices.filter((n) => n.publishStatus === params.publishStatus)
  }
  notices.sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))

  // P1-4: paginate
  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  const total = notices.length
  const start = (pageNum - 1) * pageSize
  const records = notices.slice(start, start + pageSize)

  return delayResponse({
    records,
    total,
    pages: Math.ceil(total / pageSize),
    current: pageNum,
    size: pageSize,
  }, '通知列表查询成功', 200)
}

export function getNoticeDetail(noticeId) {
  const notice = state.notices.find((n) => n.noticeId === noticeId)
  if (!notice) {
    return rejectResponse('通知不存在', 404)
  }
  if (getAuthState().user?.role === 'student' && notice.publishStatus !== 'PUBLISHED') {
    return rejectResponse('通知不存在', 404)
  }
  const fileAsset = getFileById(notice.noticeFileId)
  return delayResponse({
    ...notice,
    fileName: fileAsset?.fileName || '',
    materialRequirements: getNoticeRequirements(noticeId).map((item) => ({
      requirementId: item.requirementId,
      name: item.requirementName,
      description: item.description,
      required: item.isRequired === 1,
      sortNo: item.sortNo,
      status: item.status,
    })),
  }, '通知详情获取成功', 200)
}

// ===== P1-4：材料列表 =====

export function listMaterials(params = {}) {
  let materials = state.materials.filter((m) => m.currentVersionId != null).slice()

  if (params.projectId) {
    materials = materials.filter((m) => m.projectId === params.projectId)
  }
  if (params.submitted !== undefined && params.submitted !== null) {
    if (params.submitted) {
      materials = materials.filter((m) => m.currentVersionId != null)
    } else {
      materials = materials.filter((m) => m.currentVersionId == null)
    }
  }

  const materialViews = materials.map((m) => {
    const requirement = state.requirements.find((r) => r.requirementId === m.requirementId)
    const latestReview = state.materialReviews
      .filter((r) => r.materialVersionId === m.currentVersionId)
      .sort((a, b) => b.reviewId - a.reviewId)[0]
    return {
      materialId: m.materialId,
      requirementId: m.requirementId,
      requirementName: getRequirementName(m.requirementId),
      requiredFlag: requirement?.isRequired ?? 1,
      submitStatus: m.currentVersionId != null ? 'submitted' : 'pending',
      currentVersionId: m.currentVersionId,
      uploaded: m.currentVersionId != null && m.fileId != null,
      requirementSatisfied: requirement?.isRequired !== 1 || (m.currentVersionId != null && m.fileId != null),
      fileId: m.fileId,
      fileName: getFileById(m.fileId)?.fileName || '',
      versionNo: m.versionNo,
      submittedAt: m.submittedAt,
      reviewStatus: latestReview?.decision ?? null,
      reviewComment: latestReview?.comment ?? null,
      reviewedByName: latestReview ? getUserById(latestReview.reviewerId)?.realName ?? '' : null,
      reviewedAt: latestReview?.createdAt ?? null,
    }
  })

  const pageNum = params.pageNum || 1
  const pageSize = params.pageSize || 10
  const total = materialViews.length
  const start = (pageNum - 1) * pageSize

  return delayResponse({
    records: materialViews.slice(start, start + pageSize),
    total,
    pages: Math.ceil(total / pageSize),
    current: pageNum,
    size: pageSize,
  }, '材料列表查询成功', 200)
}

function getRequirementName(reqId) {
  const base = BASE_REQUIREMENTS[reqId - 1]
  return base?.requirementName || `材料要求 #${reqId}`
}

function getFileById(fileId) {
  return state.fileAssets.find((f) => f.fileId === fileId)
}

// ===== 新增：重置审核状态 =====

export function resetMaterialReview(materialId) {
  const material = state.materials.find((m) => m.materialId === materialId)
  if (material) {
    material.reviewStatus = null
    material.reviewComment = null
    material.reviewedBy = null
    material.reviewedByName = null
    material.reviewedAt = null
  }
  return delayResponse(null, '审核状态已重置', 200)
}

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

function createInitialState() {
  return {
    counters: {
      noticeId: 2,
      requirementId: 4,
      projectId: 2,
      materialId: 4,
      reviewId: 2,
      memberId: 4,
      fileId: 4,
    },
    users: [
      { userId: 1, username: 'admin', realName: '系统管理员', role: 'admin' },
      { userId: 2, username: 'teacher01', realName: '张老师', role: 'teacher' },
      { userId: 3, username: 'student01', realName: '李同学', role: 'student' },
      { userId: 4, username: 'student02', realName: '王同学', role: 'student' },
    ],
    fileAssets: [
      { fileId: 1, bizType: 'notice', fileName: '大学生创新创业训练计划项目申报通知.pdf' },
      { fileId: 2, bizType: 'material', fileName: '项目申报书.docx' },
      { fileId: 3, bizType: 'material', fileName: '团队成员信息表.xlsx' },
    ],
    notices: [
      {
        noticeId: 1,
        title: '大学生创新创业训练计划项目申报通知',
        organizer: '创新创业学院',
        deadline: '2026-06-15T23:59:59',
        targetGroup: '全日制本科生团队',
        rawText: '围绕创新创业训练计划项目开展申报，需提交项目申报书、团队成员信息表与指导教师意见表。',
        aiSummary: '系统已识别出截止时间、适用对象及 3 项必交申报材料。',
        noticeFileId: 1,
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
      },
      {
        requirementId: 2,
        noticeId: 1,
        requirementName: '团队成员信息表',
        isRequired: 1,
        description: '包含负责人、成员分工、联系方式及指导教师基础信息。',
        sortNo: 2,
      },
      {
        requirementId: 3,
        noticeId: 1,
        requirementName: '指导教师意见表',
        isRequired: 1,
        description: '由指导教师填写审核意见并确认签字。',
        sortNo: 3,
      },
    ],
    projects: [
      {
        projectId: 1,
        noticeId: 1,
        leaderId: 3,
        projectName: '基于大模型的校园竞赛申报材料智能核验助手',
        teamName: '逐梦智审队',
        status: 'incomplete',
        deadline: '2026-06-15T23:59:59',
        completionRate: 33.33,
      },
    ],
    projectMembers: [
      { memberId: 1, projectId: 1, userId: 3, memberRole: 'leader' },
      { memberId: 2, projectId: 1, userId: 2, memberRole: 'advisor' },
      { memberId: 3, projectId: 1, userId: 4, memberRole: 'member' },
    ],
    materials: [
      {
        materialId: 1,
        projectId: 1,
        requirementId: 1,
        fileId: 2,
        fileName: '项目申报书.docx',
        submitStatus: 'submitted',
        versionNo: 1,
        remark: '已上传项目申报书初版。',
        submittedAt: '2026-05-01T10:30:00',
      },
      {
        materialId: 2,
        projectId: 1,
        requirementId: 2,
        fileId: null,
        fileName: '',
        submitStatus: 'pending',
        versionNo: 1,
        remark: '等待团队成员完善表格并上传。',
        submittedAt: null,
      },
      {
        materialId: 3,
        projectId: 1,
        requirementId: 3,
        fileId: null,
        fileName: '',
        submitStatus: 'pending',
        versionNo: 1,
        remark: '待指导教师审核签字。',
        submittedAt: null,
      },
    ],
    reviewRecords: [
      {
        reviewId: 1,
        projectId: 1,
        reviewType: 'ai',
        reviewResult: 'warning',
        reviewComment: '当前仍缺少 2 项必交材料：团队成员信息表、指导教师意见表。',
        reviewerName: '智能核验引擎',
        createdAt: '2026-05-01T11:20:00',
      },
    ],
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

function getNoticeRequirements(noticeId) {
  return state.requirements
    .filter((item) => item.noticeId === noticeId)
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
    const material = state.materials.find(
      (item) => item.projectId === projectId && item.requirementId === requirement.requirementId,
    )

    return {
      materialId: material?.materialId ?? null,
      requirementId: requirement.requirementId,
      requirementName: requirement.requirementName,
      requiredFlag: requirement.isRequired,
      description: requirement.description,
      submitStatus: material?.submitStatus ?? 'pending',
      fileId: material?.fileId ?? null,
      fileName: material?.fileName ?? '',
      versionNo: material?.versionNo ?? 1,
      remark: material?.remark ?? '等待上传',
      submittedAt: material?.submittedAt ?? null,
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
  const submittedMaterials = materials.filter((item) => item.submitStatus === 'submitted')
  const missingMaterials = materials
    .filter((item) => item.submitStatus !== 'submitted')
    .map((item) => item.requirementName)
  const submittedTotal = submittedMaterials.length
  const missingTotal = missingMaterials.length
  const completionRate = requiredTotal ? Number(((submittedTotal / requiredTotal) * 100).toFixed(2)) : 0
  let status = 'draft'

  if (requiredTotal > 0 && submittedTotal === requiredTotal) {
    status = 'ready'
  } else if (submittedTotal > 0) {
    status = 'incomplete'
  }

  project.status = status
  project.completionRate = completionRate

  return {
    projectId,
    projectName: project.projectName,
    status,
    deadline: project.deadline,
    requiredTotal,
    submittedTotal,
    missingTotal,
    completionRate,
    missingMaterials,
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
    })
  })

  return getNoticeRequirements(notice.noticeId)
}

function buildNoticeSummary(notice, requirements) {
  const deadlineText = notice.deadline ? notice.deadline.replace('T', ' ').slice(0, 16) : '待补充'
  return `系统解析摘要：通知《${notice.title}》由 ${notice.organizer || '主办单位待补充'} 发布，截止时间为 ${deadlineText}，`
    + `识别出 ${requirements.length} 项必交材料：${requirements.map((item) => item.requirementName).join('、')}。`
}

export function getDashboardBootstrap(userId) {
  const latestNotice = state.notices[state.notices.length - 1]

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

  return delayResponse({
    notice: buildNoticeView(latestNotice),
    noticeOptions: state.notices
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

export function parseNotice(noticeId) {
  const notice = getNoticeById(noticeId)
  const requirements = ensureRequirements(notice)
  notice.aiSummary = buildNoticeSummary(notice, requirements)

  return delayResponse(
    {
      noticeId: notice.noticeId,
      title: notice.title,
      aiSummary: notice.aiSummary,
      materialRequirements: requirements.map((item) => item.requirementName),
    },
    '通知解析成功',
    360,
  )
}

export function createProject(payload) {
  const notice = getNoticeById(payload.noticeId)
  const requirements = ensureRequirements(notice)
  const deadline = payload.deadline || notice.deadline

  const project = {
    projectId: nextId('projectId'),
    noticeId: payload.noticeId,
    leaderId: payload.leaderId,
    projectName: payload.projectName,
    teamName: payload.teamName || '',
    status: 'draft',
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
      versionNo: 1,
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
  return delayResponse(buildProjectDetail(projectId), '项目详情获取成功', 180)
}

export function getProjectProgress(projectId) {
  return delayResponse(buildProjectProgress(projectId), '项目进度获取成功', 180)
}

export function uploadMaterial(payload) {
  const material = state.materials.find(
    (item) => item.projectId === payload.projectId && item.requirementId === payload.requirementId,
  )

  const fileId = nextId('fileId')
  const fileName = payload.file?.name ?? `material-${fileId}.docx`

  state.fileAssets.push({
    fileId,
    bizType: 'material',
    fileName,
  })

  if (material.fileId) {
    material.versionNo += 1
  }

  material.fileId = fileId
  material.fileName = fileName
  material.submitStatus = 'submitted'
  material.remark = payload.remark || `已上传 ${fileName}`
  material.submittedAt = new Date().toISOString()

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
  const progress = buildProjectProgress(projectId)
  const project = getProjectById(projectId)
  const missingMaterials = progress.missingMaterials
  const reviewResult = missingMaterials.length ? 'warning' : 'pass'
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
  const project = getProjectById(projectId)
  if (!project) {
    return delayResponse(null, '项目不存在', 100)
  }

  const user = getUserById(payload.userId)
  if (!user) {
    return delayResponse(null, '用户不存在', 100)
  }

  // Check for duplicate
  const exists = state.projectMembers.some(
    (pm) => pm.projectId === projectId && pm.userId === payload.userId,
  )
  if (exists) {
    return delayResponse(null, '该用户已是项目成员', 100)
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
  const index = state.projectMembers.findIndex(
    (pm) => pm.memberId === memberId && pm.projectId === projectId,
  )
  if (index === -1) {
    return delayResponse(null, '项目成员记录不存在', 100)
  }

  const member = state.projectMembers[index]
  if (member.memberRole === 'leader') {
    return delayResponse(null, '项目负责人不可移除', 100)
  }

  state.projectMembers.splice(index, 1)

  return delayResponse(null, '成员移除成功', 200)
}

// ===== 新增：文件内容查看 =====

// getFileContent 现在通过 downloadFileBlob 工作，此处保留以兼容旧调用
export function getFileContent(fileId) {
  const fileAsset = state.fileAssets.find((f) => f.fileId === fileId)
  if (!fileAsset) {
    return delayResponse(null, '文件不存在', 100)
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

// ===== 新增：我的项目列表 =====

export function getMyProjects() {
  // Simulate: return projects where user is a member
  const allProjects = state.projects.map((p) => {
    const memberRecords = state.projectMembers.filter((pm) => pm.projectId === p.projectId)
    const leaderMember = memberRecords.find((pm) => pm.memberRole === 'leader')
    const notice = getNoticeById(p.noticeId)
    const materials = buildMaterialViews(p.projectId)
    const submitted = materials.filter((m) => m.submitStatus === 'submitted').length
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

  return delayResponse(allProjects, '项目列表获取成功', 200)
}

// ===== 新增：教师审核材料 =====

export function reviewMaterial(payload) {
  const material = state.materials.find((m) => m.materialId === payload.materialId)
  if (!material) {
    return delayResponse(null, '材料记录不存在', 100)
  }

  material.reviewStatus = payload.reviewStatus
  material.reviewComment = payload.reviewComment || ''
  material.reviewedBy = payload.reviewerId
  material.reviewedByName = getUserById(payload.reviewerId)?.realName || ''
  material.reviewedAt = new Date().toISOString()

  // Add to review records
  state.reviewRecords.push({
    reviewId: state.counters.reviewId++,
    projectId: payload.projectId,
    reviewType: 'teacher',
    reviewResult: payload.reviewStatus,
    reviewComment: `材料审核结果：${payload.reviewStatus === 'approved' ? '通过' : '需修改'}。${payload.reviewComment || ''}`,
    reviewerName: material.reviewedByName,
    createdAt: material.reviewedAt,
  })

  return delayResponse(
    {
      materialId: material.materialId,
      reviewStatus: material.reviewStatus,
      reviewComment: material.reviewComment,
      reviewedAt: material.reviewedAt,
    },
    payload.reviewStatus === 'approved' ? '材料审核通过' : '已提交修改意见',
    200,
  )
}

// ===== 新增：项目审核状态 =====

export function getProjectReviewStatus(projectId) {
  const materials = buildMaterialViews(projectId)
  // Inject mock review status
  materials.forEach((m) => {
    const stored = state.materials.find(
      (sm) => sm.projectId === projectId && sm.requirementId === m.requirementId,
    )
    if (stored) {
      m.reviewStatus = stored.reviewStatus || null
      m.reviewComment = stored.reviewComment || null
      m.reviewedByName = stored.reviewedByName || null
      m.reviewedAt = stored.reviewedAt || null
    }
  })

  return delayResponse(materials, '审核状态获取成功', 200)
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

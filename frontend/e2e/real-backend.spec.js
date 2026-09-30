import { expect, test } from '@playwright/test'

const password = process.env.PHASE5_E2E_PASSWORD

async function login(page, username, expectedRole, secret = password) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(secret)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(new RegExp(`/${expectedRole}/`))
}

async function uploadPdf(page, requirementName, filename) {
  const row = page.getByRole('row', { name: new RegExp(requirementName) })
  await row.locator('input[type="file"]').setInputFiles({
    name: filename,
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF'),
  })
  await expect(row.getByText(filename)).toBeVisible()
}

test('真实后端：管理员发布 → 学生申报/重提 → 指导教师退回/通过', async ({ browser }) => {
  if (!password) throw new Error('PHASE5_E2E_PASSWORD is required for the isolated real-backend run')
  const adminContext = await browser.newContext()
  const admin = await adminContext.newPage()
  await login(admin, 'admin', 'admin')
  await admin.goto('/admin/notices/1')
  await expect(admin.getByText('Phase5 真实接口隔离验收通知').first()).toBeVisible()
  if (await admin.getByRole('button', { name: '发布通知' }).count()) {
    await admin.getByRole('button', { name: '发布通知' }).click()
    await admin.getByRole('dialog', { name: '发布通知' }).getByRole('button', { name: '确认发布' }).click()
  }
  await expect(admin.getByRole('button', { name: '归档通知' })).toBeVisible()

  const studentContext = await browser.newContext()
  const student = await studentContext.newPage()
  await login(student, 'student1', 'student')
  await student.goto('/student/projects/new?noticeId=1')
  await student.getByPlaceholder('请输入申报项目名称').fill('Phase5 真实接口端到端项目')
  await student.getByPlaceholder('请输入团队名称').fill('隔离验收团队')
  await student.getByText('搜索姓名并选择成员', { exact: true }).first().click()
  await student.getByRole('option', { name: /张老师/ }).click()
  await student.getByRole('button', { name: '创建项目' }).click()
  await expect(student).toHaveURL(/\/student\/projects\/\d+$/)
  const projectId = Number(student.url().split('/').pop())

  await student.getByRole('tab', { name: '材料' }).click()
  await uploadPdf(student, '项目申报书', 'proposal-v1.pdf')
  await uploadPdf(student, '指导教师意见表', 'advisor-v1.pdf')
  await student.getByRole('tab', { name: '概览' }).click()
  await expect(student.getByText('100%').first()).toBeVisible()
  await student.getByRole('tab', { name: '材料' }).click()
  await student.getByRole('button', { name: '提交审核' }).click()
  await student.getByRole('dialog', { name: '提交确认' }).getByRole('button', { name: 'OK' }).click()
  await expect(student.getByRole('button', { name: '已提交审核' })).toBeDisabled()

  const teacherContext = await browser.newContext()
  const teacher = await teacherContext.newPage()
  await login(teacher, 'teacher1', 'teacher')
  await teacher.goto(`/teacher/projects/${projectId}/review`)
  // 未上传占位为 V0，首个真实上传文件为 V1。
  const proposalRow = teacher.getByRole('row', { name: /项目申报书.*V1/ })
  await proposalRow.getByRole('button', { name: '查看文件' }).click()
  await expect(teacher.getByRole('dialog', { name: 'proposal-v1.pdf' }).locator('iframe')).toBeVisible()
  await teacher.getByRole('dialog', { name: 'proposal-v1.pdf' }).getByRole('button', { name: '关闭' }).click()
  await expect(teacher.getByRole('dialog', { name: 'proposal-v1.pdf' })).toBeHidden()
  await proposalRow.getByRole('button', { name: '通过' }).click()
  await expect(proposalRow.getByText('材料通过')).toBeVisible()
  await teacher.getByRole('button', { name: '退回修改' }).click()
  const revisionDialog = teacher.getByRole('dialog', { name: '填写退回修改意见' })
  await revisionDialog.getByPlaceholder('请说明退回原因和需要修改的内容，学生将收到此意见。').fill('请更新教师签字意见表后重新提交')
  await revisionDialog.getByRole('button', { name: '确认退回' }).click()
  await expect(teacher.getByText('项目已退回修改，等待学生修改后重新提交。')).toBeVisible()

  await student.goto(`/student/projects/${projectId}`)
  await student.getByRole('tab', { name: '材料' }).click()
  await uploadPdf(student, '指导教师意见表', 'advisor-v2.pdf')
  await expect(student.getByRole('row', { name: /指导教师意见表.*V2/ })).toBeVisible()
  await student.getByRole('tab', { name: '概览' }).click()
  await expect(student.getByText('100%').first()).toBeVisible()
  await student.getByRole('tab', { name: '材料' }).click()
  await student.getByRole('button', { name: '重新提交审核' }).click()
  await student.getByRole('dialog', { name: '提交确认' }).getByRole('button', { name: 'OK' }).click()
  await expect(student.getByRole('button', { name: '已提交审核' })).toBeDisabled()

  await teacher.goto(`/teacher/projects/${projectId}/review`)
  const latestRow = teacher.getByRole('row', { name: /指导教师意见表.*V2/ })
  await expect(latestRow.getByText('未审核')).toBeVisible()
  await latestRow.getByRole('button', { name: '通过' }).click()
  await expect(teacher.getByRole('button', { name: '通过项目' })).toBeEnabled()
  await teacher.getByRole('button', { name: '通过项目' }).click()
  await teacher.getByRole('dialog', { name: '确认操作' }).getByRole('button', { name: '确认通过' }).click()
  await expect(teacher.getByText('项目已审核通过。')).toBeVisible()

  await student.goto('/messages')
  await expect(student.getByText(/已审核通过/).first()).toBeVisible()
  await student.getByRole('button', { name: '查看项目' }).first().click()
  await expect(student).toHaveURL(new RegExp(`/student/projects/${projectId}$`))
  await teacherContext.close()
  await studentContext.close()
  await adminContext.close()
})

test('真实后端：修改密码后立即注销，新密码可重新登录', async ({ page }) => {
  if (!password) throw new Error('PHASE5_E2E_PASSWORD is required for the isolated real-backend run')
  await login(page, 'student2', 'student')
  await page.locator('.app-shell__user-area').click()
  await page.getByRole('button', { name: '个人信息' }).click()
  const profile = page.getByRole('dialog', { name: '个人信息维护' })
  await profile.getByRole('tab', { name: '修改密码' }).click()
  await profile.getByPlaceholder('请输入原密码').fill(password)
  await profile.getByPlaceholder('请输入新密码（至少6位）').fill(`${password}-changed`)
  await profile.getByPlaceholder('请再次输入新密码').fill(`${password}-changed`)
  await profile.getByRole('button', { name: '修改密码' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await login(page, 'student2', 'student', `${password}-changed`)
})

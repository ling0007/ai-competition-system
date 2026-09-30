import { expect, test } from '@playwright/test'

async function login(page, username) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill('123456')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(new RegExp(`/${username.startsWith('student') ? 'student' : username.startsWith('teacher') ? 'teacher' : 'admin'}/`))
}

test('管理员可进入通知运营页并看到草稿与发布动作', async ({ page }) => {
  await login(page, 'admin')
  await expect(page).toHaveURL(/\/admin\/users/)
  await page.getByRole('button', { name: '通知管理与解析' }).click()
  await expect(page).toHaveURL(/\/admin\/notices/)
  await expect(page.getByText('2026 年校园科技创新竞赛通知')).toBeVisible()
  await page.goto('/admin/notices/2')
  await page.getByRole('button', { name: '开始 AI 解析' }).click()
  await expect(page.getByRole('heading', { name: 'AI 解析草稿 · 人工确认' })).toBeVisible()
  await page.getByRole('button', { name: '确认并写入正式数据' }).click()
  await page.getByRole('dialog', { name: '确认解析结果' }).getByRole('button', { name: '保存并确认' }).click()
  await page.getByRole('button', { name: '发布通知' }).click()
  await page.getByRole('dialog', { name: '发布通知' }).getByRole('button', { name: '确认发布' }).click()
  await expect(page.getByRole('button', { name: '归档通知' })).toBeVisible()
})

test('管理员项目空结果不拉长页面，学生通知操作按钮对齐', async ({ page }) => {
  await login(page, 'admin')
  await page.goto('/admin/projects')
  await page.getByPlaceholder('搜索项目名称、负责人或通知...').fill('不存在的项目-空态检查')
  await page.getByRole('button', { name: '查询' }).click()
  await expect(page.getByText('暂无项目数据')).toBeVisible()
  const emptyHeight = await page.locator('.project-list-panel__table').evaluate((element) => element.getBoundingClientRect().height)
  expect(emptyHeight).toBeLessThan(500)

  await page.evaluate(() => localStorage.removeItem('auth_token'))
  await page.reload()
  await login(page, 'student01')
  await page.goto('/student/notices')
  const actions = page.locator('.notice-list__row-actions').first()
  await expect(actions.getByRole('button', { name: '查看详情' })).toBeVisible()
  await expect(actions.getByRole('button', { name: '立即申报' })).toBeVisible()
  const centers = await actions.getByRole('button').evaluateAll((buttons) =>
    buttons.map((button) => {
      const box = button.getBoundingClientRect()
      return box.left + box.width / 2
    }))
  expect(Math.abs(centers[0] - centers[1])).toBeLessThan(1)
})

test('管理员可重传待解析通知附件', async ({ page }) => {
  await login(page, 'admin')
  await page.goto('/admin/notices/2')
  await expect(page.getByRole('button', { name: '上传附件' }).first()).toBeVisible()
  await page.locator('.notice-detail-view__actions input[type="file"]').setInputFiles('../docs/testdata/manual-ai-notice.txt')
  await page.getByRole('dialog', { name: '确认替换附件' }).getByRole('button', { name: '替换附件' }).click()
  await expect(page.getByText('manual-ai-notice.txt')).toBeVisible()
  await expect(page.getByRole('button', { name: '开始 AI 解析' })).toBeVisible()
})

test('新建通知时可直接重新选择附件', async ({ page }) => {
  await login(page, 'admin')
  await page.goto('/admin/notices/new')
  const uploadInput = page.locator('.notice-panel__actions input[type="file"]')
  await uploadInput.setInputFiles('../docs/testdata/manual-ai-notice.txt')
  await expect(page.getByText('manual-ai-notice.txt')).toBeVisible()
  await uploadInput.setInputFiles('../docs/testdata/manual-project-proposal.txt')
  await expect(page.getByText('manual-project-proposal.txt')).toBeVisible()
  await expect(page.getByText('manual-ai-notice.txt')).toHaveCount(0)
})

test('学生从已发布通知进入项目工作区并可查看当前进度', async ({ page }) => {
  await login(page, 'student01')
  await expect(page).toHaveURL(/\/student\//)
  await page.goto('/student/projects/1')
  await expect(page.getByText('基于大模型的校园竞赛申报材料智能核验助手').first()).toBeVisible()
  await expect(page.getByText('100%').first()).toBeVisible()
  await page.getByRole('tab', { name: '材料' }).click()
  await page.getByRole('button', { name: '提交审核' }).first().click()
  await page.getByRole('dialog', { name: '提交确认' }).getByRole('button', { name: 'OK' }).click()
  await expect(page.getByText('待审核').first()).toBeVisible()
  await expect(page.getByRole('button', { name: '已提交审核' })).toBeDisabled()
})

test('教师待办受 advisor 归属限制，能进入当前版本审核', async ({ page }) => {
  await login(page, 'teacher02')
  await expect(page).toHaveURL(/\/teacher\/dashboard/)
  await expect(page.getByText('待审核项目：')).toContainText('1')
  await expect(page.getByText('智能校园导航系统').first()).toBeVisible()
  await page.getByRole('button', { name: '查看审核' }).first().click()
  await expect(page).toHaveURL(/\/teacher\/projects\/2\/review/)
  await expect(page.getByRole('row', { name: /指导教师意见表.*V1/ })).toBeVisible()
  await page.getByRole('button', { name: '退回修改' }).click()
  await page.getByRole('dialog', { name: '填写退回修改意见' }).getByPlaceholder('请说明退回原因和需要修改的内容，学生将收到此意见。').fill('请补齐并重新提交签字材料')
  await page.getByRole('dialog', { name: '填写退回修改意见' }).getByRole('button', { name: '确认退回' }).click()
  await expect(page.getByText('项目已退回修改，等待学生修改后重新提交。')).toBeVisible()
  await page.goto('/teacher/projects/1/review')
  await expect(page.getByText('无权访问该项目').first()).toBeVisible()
})

test('教师可重新审核当前版本并通过项目', async ({ page }) => {
  await login(page, 'teacher02')
  await page.goto('/teacher/projects/2/review')
  const opinionRow = page.getByRole('row', { name: /指导教师意见表.*V1/ })
  await opinionRow.getByRole('button', { name: '重新审核' }).click()
  await opinionRow.getByRole('button', { name: '通过' }).click()
  await expect(page.getByRole('button', { name: '通过项目' })).toBeEnabled()
  await page.getByRole('button', { name: '通过项目' }).click()
  await page.getByRole('dialog', { name: '确认操作' }).getByRole('button', { name: '确认通过' }).click()
  await expect(page.getByText('项目已审核通过。')).toBeVisible()
})

test('学生补齐退回项目材料并重新提交', async ({ page }) => {
  await login(page, 'student05')
  await page.goto('/student/projects/5')
  await page.getByRole('tab', { name: '材料' }).click()
  const opinionRow = page.getByRole('row', { name: /指导教师意见表/ })
  await opinionRow.locator('input[type="file"]').setInputFiles({
    name: 'teacher-opinion.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF'),
  })
  await page.getByRole('tab', { name: '概览' }).click()
  await expect(page.getByText('100%').first()).toBeVisible()
  await page.getByRole('tab', { name: '材料' }).click()
  await expect(page.getByRole('button', { name: '重新提交审核' })).toBeEnabled()
  await page.getByRole('button', { name: '重新提交审核' }).click()
  await page.getByRole('dialog', { name: '提交确认' }).getByRole('button', { name: 'OK' }).click()
  await expect(page.getByRole('button', { name: '已提交审核' })).toBeDisabled()
})

test('窄屏仍可使用项目关键动作与导航', async ({ page }) => {
  await login(page, 'student01')
  await page.goto('/student/projects/1')
  for (const width of [1024, 390]) {
    await page.setViewportSize({ width, height: 844 })
    await expect(page.getByRole('navigation', { name: '后台导航' })).toBeVisible()
    await page.getByRole('tab', { name: '材料' }).click()
    await expect(page.getByRole('button', { name: '提交审核' })).toBeVisible()
  }
})

test('附件链接透明，消息操作双蓝色按钮且已读行对齐', async ({ page }) => {
  await login(page, 'admin')
  await page.goto('/admin/notices/1')
  const attachment = page.getByRole('button', { name: /大学生创新创业训练计划项目申报通知/ })
  await expect(attachment).toBeVisible()
  await expect(attachment).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)')

  await page.evaluate(() => localStorage.removeItem('auth_token'))
  await page.reload()
  await login(page, 'student01')
  await page.goto('/messages')
  const unreadRow = page.locator('.msg-item--unread').first()
  const projectButton = unreadRow.getByRole('button', { name: '查看项目' })
  const readButton = unreadRow.getByRole('button', { name: '标为已读' })
  for (const button of [projectButton, readButton]) {
    await expect(button).toHaveCSS('background-color', 'rgb(59, 130, 246)')
    await expect(button).toHaveCSS('color', 'rgb(255, 255, 255)')
  }

  for (const width of [1024, 390]) {
    await page.setViewportSize({ width, height: 844 })
    const positions = await page.locator('.msg-item').evaluateAll((rows) => rows
      .filter((row) => row.querySelector('.msg-item__project-button'))
      .map((row) => row.querySelector('.msg-item__project-button').getBoundingClientRect().x))
    expect(positions.length).toBeGreaterThan(1)
    expect(Math.max(...positions) - Math.min(...positions)).toBeLessThan(1)
  }

  const rowContent = await unreadRow.locator('.msg-item__content').innerText()
  const before = await projectButton.boundingBox()
  const beforeRow = await unreadRow.boundingBox()
  await readButton.click()
  const markedRow = page.locator('.msg-item').filter({ hasText: rowContent })
  await expect(markedRow.getByText('已读', { exact: true })).toBeVisible()
  const after = await markedRow.getByRole('button', { name: '查看项目' }).boundingBox()
  const afterRow = await markedRow.boundingBox()
  // 点击右侧按钮可能触发页面横向滚动；比较按钮相对所在行的位置。
  expect(Math.abs((after.x - afterRow.x) - (before.x - beforeRow.x))).toBeLessThanOrEqual(2)
})

test('材料表格操作按钮完整落在固定操作列内', async ({ page }) => {
  async function assertActionsFit(selector) {
    const result = await page.locator(selector).evaluateAll((cells) => cells.flatMap((cell) => {
      const bounds = cell.getBoundingClientRect()
      return [...cell.querySelectorAll('button')].filter((button) => button.getClientRects().length).map((button) => {
        const buttonBounds = button.getBoundingClientRect()
        return {
          label: button.textContent.trim(),
          fits: buttonBounds.left >= bounds.left - 1 && buttonBounds.right <= bounds.right + 1,
        }
      })
    }))
    expect(result.length).toBeGreaterThan(0)
    expect(result.filter((item) => !item.fits)).toEqual([])
  }

  await login(page, 'teacher02')
  await page.goto('/teacher/projects/2/review')
  await expect(page.locator('.review-panel__actions').first()).toBeVisible()
  for (const width of [1280, 1024, 390]) {
    await page.setViewportSize({ width, height: 844 })
    await assertActionsFit('.review-panel__table td:has(.review-panel__actions)')
  }

  await page.evaluate(() => localStorage.removeItem('auth_token'))
  await page.reload()
  await login(page, 'student01')
  await page.goto('/student/projects/1')
  await page.getByRole('tab', { name: '材料' }).click()
  await expect(page.locator('.material-panel__actions').first()).toBeVisible()
  for (const width of [1280, 1024, 390]) {
    await page.setViewportSize({ width, height: 844 })
    await assertActionsFit('td:has(.material-panel__actions)')
  }

  await page.goto('/student/notices')
  await expect(page.getByRole('button', { name: '立即申报' }).first()).toBeVisible()
  for (const width of [1280, 1024, 390]) {
    await page.setViewportSize({ width, height: 844 })
    await assertActionsFit('td:has(button:has-text("立即申报"))')
  }
})

test('消息中心仅学生可见，教师和管理员无法从前端路由进入', async ({ page }) => {
  for (const username of ['teacher02', 'admin']) {
    await login(page, username)
    await expect(page.getByRole('navigation', { name: '后台导航' }).getByRole('button', { name: '消息中心' })).toHaveCount(0)
    await page.goto('/messages')
    await expect(page.getByRole('heading', { name: '无权访问此页面' })).toBeVisible()
    await page.evaluate(() => localStorage.removeItem('auth_token'))
    await page.reload()
  }

  await login(page, 'student01')
  await expect(page.getByRole('navigation', { name: '后台导航' }).getByRole('button', { name: '消息中心' })).toBeVisible()
  await page.goto('/messages')
  await expect(page.getByText('消息中心', { exact: true }).last()).toBeVisible()
})

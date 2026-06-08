import { MOCK_USERS, getNextId, makeFakeToken } from '@/mock/authService'

function delayResponse(data, message = 'success', ms = 280) {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve({ code: 200, message, data, timestamp: new Date().toISOString() })
    }, ms)
  })
}

function resolveUserFromToken() {
  const token = localStorage.getItem('auth_token')
  if (!token) {
    throw new Error('未登录')
  }
  try {
    const payload = JSON.parse(atob(token))
    const userId = Number(payload.sub)
    const user = MOCK_USERS.find((u) => u.userId === userId)
    if (!user) {
      throw new Error('用户不存在')
    }
    return user
  } catch (e) {
    if (e.message === '未登录' || e.message === '用户不存在') {
      throw e
    }
    throw new Error('Token无效')
  }
}

// ===== Profile APIs =====

export function getUserProfile() {
  try {
    const user = resolveUserFromToken()
    return delayResponse({
      userId: user.userId,
      username: user.username,
      realName: user.realName,
      role: user.role,
      phone: user.phone,
      createdAt: '2026-01-01T00:00:00',
    })
  } catch (e) {
    return Promise.reject({ response: { data: { message: e.message } } })
  }
}

export function updateProfile({ username, realName, phone }) {
  try {
    const user = resolveUserFromToken()
    // Check username uniqueness
    if (username !== user.username && MOCK_USERS.some((u) => u.username === username)) {
      return Promise.reject({ response: { data: { message: '用户名已被其他用户使用' } } })
    }
    user.username = username
    user.realName = realName
    user.phone = phone || ''
    return delayResponse(
      {
        userId: user.userId,
        username: user.username,
        realName: user.realName,
        role: user.role,
        phone: user.phone,
        createdAt: '2026-01-01T00:00:00',
      },
      '个人信息更新成功',
    )
  } catch (e) {
    return Promise.reject({ response: { data: { message: e.message } } })
  }
}

export function changePassword({ oldPassword, newPassword }) {
  try {
    const user = resolveUserFromToken()
    if (user.password !== oldPassword) {
      return Promise.reject({ response: { data: { message: '原密码不正确' } } })
    }
    if (!newPassword || newPassword.length < 6) {
      return Promise.reject({ response: { data: { message: '新密码长度不能少于6位' } } })
    }
    user.password = newPassword
    return delayResponse(null, '密码修改成功，请重新登录')
  } catch (e) {
    return Promise.reject({ response: { data: { message: e.message } } })
  }
}

// ===== Admin APIs =====

export function getAdminUserList(keyword) {
  let users = [...MOCK_USERS]
  if (keyword && keyword.trim()) {
    const kw = keyword.trim().toLowerCase()
    users = users.filter(
      (u) =>
        u.username.toLowerCase().includes(kw) ||
        u.realName.toLowerCase().includes(kw),
    )
  }
  const list = users.map((u) => ({
    userId: u.userId,
    username: u.username,
    realName: u.realName,
    role: u.role,
    phone: u.phone,
    createdAt: '2026-01-01T00:00:00',
  }))
  return delayResponse(list)
}

export function updateUserRole(userId, role) {
  const user = MOCK_USERS.find((u) => u.userId === userId)
  if (!user) {
    return Promise.reject({ response: { data: { message: '用户不存在' } } })
  }
  if (!['student', 'teacher', 'admin'].includes(role)) {
    return Promise.reject({ response: { data: { message: '无效的角色' } } })
  }
  user.role = role
  return delayResponse(
    {
      userId: user.userId,
      username: user.username,
      realName: user.realName,
      role: user.role,
      phone: user.phone,
      createdAt: '2026-01-01T00:00:00',
    },
    '角色更新成功',
  )
}

export function createUser({ username, password, realName, role, phone }) {
  if (!username || username.length < 3) {
    return Promise.reject({ response: { data: { message: '用户名长度需在3-50个字符之间' } } })
  }
  if (!password || password.length < 6) {
    return Promise.reject({ response: { data: { message: '密码长度不能少于6位' } } })
  }
  if (MOCK_USERS.some((u) => u.username === username)) {
    return Promise.reject({ response: { data: { message: '用户名已存在' } } })
  }
  const user = {
    userId: getNextId(),
    username,
    password,
    realName,
    role,
    phone: phone || '',
  }
  MOCK_USERS.push(user)
  return delayResponse(
    {
      userId: user.userId,
      username: user.username,
      realName: user.realName,
      role: user.role,
      phone: user.phone,
      createdAt: new Date().toISOString(),
    },
    '用户创建成功',
  )
}

export function deleteUser(userId) {
  const idx = MOCK_USERS.findIndex((u) => u.userId === userId)
  if (idx === -1) {
    return Promise.reject({ response: { data: { message: '用户不存在' } } })
  }
  MOCK_USERS.splice(idx, 1)
  return delayResponse(null, '用户删除成功')
}

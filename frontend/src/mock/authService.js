export const MOCK_USERS = [
  { userId: 1, username: 'admin', password: '123456', realName: '系统管理员', role: 'admin', phone: '13800000001' },
  { userId: 2, username: 'teacher01', password: '123456', realName: '张老师', role: 'teacher', phone: '13800000002' },
  { userId: 3, username: 'student01', password: '123456', realName: '李同学', role: 'student', phone: '13800000003' },
]

export let nextId = 4

export function getNextId() {
  return nextId++
}

function delayResponse(data, message = 'success', ms = 280) {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      resolve({ code: 200, message, data, timestamp: new Date().toISOString() })
    }, ms)
  })
}

export function makeFakeToken(user) {
  const payload = {
    sub: String(user.userId),
    username: user.username,
    role: user.role,
    exp: Date.now() + 86400000,
    iat: Date.now(),
  }
  return btoa(JSON.stringify(payload))
}

export function login({ username, password }) {
  const user = MOCK_USERS.find((u) => u.username === username && u.password === password)
  if (!user) {
    return Promise.reject({
      response: { data: { message: '用户名或密码错误' } },
    })
  }
  return delayResponse(
    {
      token: makeFakeToken(user),
      userId: user.userId,
      username: user.username,
      realName: user.realName,
      role: user.role,
      phone: user.phone,
    },
    '登录成功',
  )
}

export function register({ username, password, confirmPassword, realName, role, phone }) {
  if (password !== confirmPassword) {
    return Promise.reject({
      response: { data: { message: '两次输入的密码不一致' } },
    })
  }
  if (MOCK_USERS.some((u) => u.username === username)) {
    return Promise.reject({
      response: { data: { message: '用户名已存在' } },
    })
  }
  const user = { userId: nextId++, username, password, realName, role, phone: phone || '' }
  MOCK_USERS.push(user)
  return delayResponse(
    {
      token: makeFakeToken(user),
      userId: user.userId,
      username: user.username,
      realName: user.realName,
      role: user.role,
      phone: user.phone,
    },
    '注册成功',
  )
}

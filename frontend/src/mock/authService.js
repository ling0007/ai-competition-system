export const MOCK_USERS = [
  { userId: 1, username: 'admin', password: '123456', realName: '系统管理员', role: 'admin', phone: '13800000001' },
  { userId: 2, username: 'teacher01', password: '123456', realName: '张老师', role: 'teacher', phone: '13800000002' },
  { userId: 3, username: 'student01', password: '123456', realName: '李同学', role: 'student', phone: '13800000003' },
  { userId: 4, username: 'student02', password: '123456', realName: '王同学', role: 'student', phone: '13800000004' },
  { userId: 5, username: 'student03', password: '123456', realName: '赵同学', role: 'student', phone: '13800000005' },
  { userId: 6, username: 'student04', password: '123456', realName: '陈同学', role: 'student', phone: '13800000006' },
  { userId: 7, username: 'student05', password: '123456', realName: '刘同学', role: 'student', phone: '13800000007' },
  { userId: 8, username: 'teacher02', password: '123456', realName: '周老师', role: 'teacher', phone: '13800000008' },
  { userId: 9, username: 'teacher03', password: '123456', realName: '吴老师', role: 'teacher', phone: '13800000009' },
  { userId: 10, username: 'student06', password: '123456', realName: '孙同学', role: 'student', phone: '13800000010' },
  { userId: 11, username: 'student07', password: '123456', realName: '黄同学', role: 'student', phone: '13800000011' },
  { userId: 12, username: 'student08', password: '123456', realName: '杨同学', role: 'student', phone: '13800000012' },
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

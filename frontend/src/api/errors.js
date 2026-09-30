/** Shared presentation text for API failures. The backend message remains authoritative. */
export function resolveApiErrorMessage(error, fallback = '操作失败，请稍后重试') {
  const status = error?.response?.status
  const fromServer = error?.response?.data?.message
  if (fromServer) return fromServer
  if (status === 400) return '请求内容有误，请检查后重试'
  if (status === 401) return '登录已失效，请重新登录'
  if (status === 403) return '无权执行该操作'
  if (status === 404) return '资源不存在或已被删除'
  if (status === 409) return '数据已变化，请刷新后重试'
  if (status >= 500) return '服务器暂时不可用，请稍后重试'
  if (!error?.response && error?.isAxiosError) return '网络连接失败，请检查网络后重试'
  return error?.message || fallback
}

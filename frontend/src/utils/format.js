function pad(value) {
  return String(value).padStart(2, '0')
}

function normalizeDate(value) {
  if (!value) {
    return null
  }

  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatDateTime(value) {
  const date = normalizeDate(value)
  if (!date) {
    return '未设置'
  }

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatPercent(value) {
  const numericValue = Number(value ?? 0)
  if (Number.isNaN(numericValue)) {
    return '0%'
  }

  return `${numericValue.toFixed(numericValue % 1 === 0 ? 0 : 2)}%`
}

export function toIsoLocalDateTime(value) {
  const date = normalizeDate(value)
  if (!date) {
    return null
  }

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

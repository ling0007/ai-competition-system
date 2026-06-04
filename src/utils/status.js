const STATUS_MAP = {
  draft: {
    label: '待完善',
    tagType: 'info',
    accent: 'slate',
  },
  incomplete: {
    label: '材料待补全',
    tagType: 'danger',
    accent: 'red',
  },
  ready: {
    label: '可提交',
    tagType: 'success',
    accent: 'green',
  },
  submitted: {
    label: '已提交',
    tagType: 'success',
    accent: 'green',
  },
  pending: {
    label: '待上传',
    tagType: 'info',
    accent: 'slate',
  },
  pass: {
    label: '核验通过',
    tagType: 'success',
    accent: 'green',
  },
  warning: {
    label: '待补全',
    tagType: 'danger',
    accent: 'red',
  },
}

export function resolveStatusMeta(status) {
  return STATUS_MAP[status] ?? {
    label: status || '未知状态',
    tagType: 'info',
    accent: 'slate',
  }
}

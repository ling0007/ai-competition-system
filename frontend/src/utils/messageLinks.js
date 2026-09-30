export function projectMessageTarget(role, projectId) {
  if (!Number.isSafeInteger(Number(projectId)) || Number(projectId) <= 0) return null
  if (role === 'teacher') return `/teacher/projects/${projectId}/review`
  if (role === 'admin') return `/admin/projects/${projectId}`
  if (role === 'student') return `/student/projects/${projectId}`
  return null
}

INSERT INTO sys_user (
    user_id,
    username,
    password,
    real_name,
    role,
    phone
) VALUES
    (1, 'admin', '$2a$10$7QJ3s3W2FlM4mN4lKXgD3OLG5DZqG4h1K9W6t8.HtD7u2rJ3N4N9e', 'System Admin', 'admin', '13800000001'),
    (2, 'teacher01', '$2a$10$7QJ3s3W2FlM4mN4lKXgD3OLG5DZqG4h1K9W6t8.HtD7u2rJ3N4N9e', 'Teacher Zhang', 'teacher', '13800000002'),
    (3, 'student01', '$2a$10$7QJ3s3W2FlM4mN4lKXgD3OLG5DZqG4h1K9W6t8.HtD7u2rJ3N4N9e', 'Student Li', 'student', '13800000003')
ON DUPLICATE KEY UPDATE
    username = VALUES(username),
    password = VALUES(password),
    real_name = VALUES(real_name),
    role = VALUES(role),
    phone = VALUES(phone),
    is_deleted = 0;

INSERT INTO file_asset (
    file_id,
    biz_type,
    file_name,
    file_ext,
    file_size,
    storage_path,
    uploaded_by
) VALUES
    (1, 'notice', 'university-innovation-notice.pdf', 'pdf', 245760, 'notice/2026/university-innovation-notice.pdf', 1),
    (2, 'material', 'project-application-book-v1.docx', 'docx', 102400, 'material/2026/project-application-book-v1.docx', 3),
    (3, 'material', 'team-member-sheet-v1.xlsx', 'xlsx', 51200, 'material/2026/team-member-sheet-v1.xlsx', 3)
ON DUPLICATE KEY UPDATE
    biz_type = VALUES(biz_type),
    file_name = VALUES(file_name),
    file_ext = VALUES(file_ext),
    file_size = VALUES(file_size),
    storage_path = VALUES(storage_path),
    uploaded_by = VALUES(uploaded_by),
    is_deleted = 0;

INSERT INTO competition_notice (
    notice_id,
    title,
    organizer,
    deadline,
    target_group,
    raw_text,
    ai_summary,
    notice_file_id,
    created_by
) VALUES (
    1,
    'University Innovation Competition Notice',
    'Innovation School',
    '2026-06-15 23:59:59',
    'Undergraduate student teams',
    'Students should submit the application book, team member sheet, and advisor comment form. The system supports online checking and progress tracking.',
    '【AI解析】主办方：Innovation School；截止时间：2026-06-15 23:59；面向对象：Undergraduate student teams；关键内容：Students should submit the application book, team member sheet, and advisor comment form. The system supports online checking and progress tracking.；共识别 3 项材料要求：Project Application、Team Member Sheet、Advisor Comment Form。',
    1,
    1
)
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    organizer = VALUES(organizer),
    deadline = VALUES(deadline),
    target_group = VALUES(target_group),
    raw_text = VALUES(raw_text),
    ai_summary = VALUES(ai_summary),
    notice_file_id = VALUES(notice_file_id),
    created_by = VALUES(created_by),
    is_deleted = 0;

INSERT INTO material_requirement (
    requirement_id,
    notice_id,
    requirement_name,
    is_required,
    description,
    sort_no
) VALUES
    (1, 1, 'Project Application', 1, 'Project overview, innovation points, and implementation plan.', 1),
    (2, 1, 'Team Member Sheet', 1, 'Member names, responsibilities, and contact details.', 2),
    (3, 1, 'Advisor Comment Form', 1, 'Advisor comments and signature confirmation.', 3)
ON DUPLICATE KEY UPDATE
    notice_id = VALUES(notice_id),
    requirement_name = VALUES(requirement_name),
    is_required = VALUES(is_required),
    description = VALUES(description),
    sort_no = VALUES(sort_no),
    is_deleted = 0;

INSERT INTO competition_project (
    project_id,
    notice_id,
    leader_id,
    project_name,
    team_name,
    status,
    deadline,
    completion_rate
) VALUES (
    1,
    1,
    3,
    'AI Campus Competition Material Assistant',
    'Dream Team',
    'draft',
    '2026-06-15 23:59:59',
    0.00
)
ON DUPLICATE KEY UPDATE
    notice_id = VALUES(notice_id),
    leader_id = VALUES(leader_id),
    project_name = VALUES(project_name),
    team_name = VALUES(team_name),
    status = VALUES(status),
    deadline = VALUES(deadline),
    completion_rate = VALUES(completion_rate),
    is_deleted = 0;

INSERT INTO project_member (
    member_id,
    project_id,
    user_id,
    member_role
) VALUES
    (1, 1, 3, 'leader'),
    (2, 1, 2, 'advisor')
ON DUPLICATE KEY UPDATE
    project_id = VALUES(project_id),
    user_id = VALUES(user_id),
    member_role = VALUES(member_role),
    is_deleted = 0;

INSERT INTO project_material (
    material_id,
    project_id,
    requirement_id,
    file_id,
    submit_status,
    version_no,
    remark,
    submitted_at
) VALUES
    (1, 1, 1, 2, 'submitted', 1, 'First version uploaded.', '2026-05-01 10:30:00'),
    (2, 1, 2, NULL, 'pending', 1, 'Waiting for team member sheet upload.', NULL),
    (3, 1, 3, NULL, 'pending', 1, 'Waiting for advisor confirmation.', NULL)
ON DUPLICATE KEY UPDATE
    project_id = VALUES(project_id),
    requirement_id = VALUES(requirement_id),
    file_id = VALUES(file_id),
    submit_status = VALUES(submit_status),
    version_no = VALUES(version_no),
    remark = VALUES(remark),
    submitted_at = VALUES(submitted_at),
    is_deleted = 0;

INSERT INTO review_record (
    review_id,
    project_id,
    reviewer_id,
    review_type,
    review_result,
    review_comment
) VALUES (
    1,
    1,
    NULL,
    'ai',
    'warning',
    '【材料缺失】以下材料尚未提交：Team Member Sheet、Advisor Comment Form。\n【内容审核】系统检查：项目材料不完整，请尽快补充缺失材料。'
)
ON DUPLICATE KEY UPDATE
    project_id = VALUES(project_id),
    reviewer_id = VALUES(reviewer_id),
    review_type = VALUES(review_type),
    review_result = VALUES(review_result),
    review_comment = VALUES(review_comment),
    is_deleted = 0;

INSERT INTO notify_message (
    msg_id,
    project_id,
    receiver_id,
    msg_type,
    msg_content,
    is_read
) VALUES (
    1,
    1,
    3,
    'material',
    'Your project still lacks 2 required materials. Please upload them as soon as possible.',
    0
)
ON DUPLICATE KEY UPDATE
    project_id = VALUES(project_id),
    receiver_id = VALUES(receiver_id),
    msg_type = VALUES(msg_type),
    msg_content = VALUES(msg_content),
    is_read = VALUES(is_read),
    is_deleted = 0;

INSERT INTO agent_task_log (
    task_id,
    project_id,
    tool_name,
    input_summary,
    result_summary,
    execute_status
) VALUES
    (
        1,
        1,
        'parseNoticeTool',
        '解析通知: University Innovation Competition Notice (237 字符)',
        '【AI解析】主办方：Innovation School；截止时间：2026-06-15 23:59...',
        'success'
    ),
    (
        2,
        1,
        'checkMaterialTool',
        '审核项目材料: AI Campus Competition Material Assistant, 已提交=1/3, 可审核文件=1',
        '【材料缺失】以下材料尚未提交：Team Member Sheet、Advisor Comment Form...',
        'success'
    )
ON DUPLICATE KEY UPDATE
    project_id = VALUES(project_id),
    tool_name = VALUES(tool_name),
    input_summary = VALUES(input_summary),
    result_summary = VALUES(result_summary),
    execute_status = VALUES(execute_status),
    is_deleted = 0;

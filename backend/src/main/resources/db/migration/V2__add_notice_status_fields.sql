-- ============================================================
-- P0-5: 为 competition_notice 表新增状态管理字段
-- 支持通知从上传 → AI解析 → 确认 → 发布 → 归档的完整生命周期
-- ============================================================

-- 1. 通知类型（支持多种申报场景）
ALTER TABLE competition_notice
ADD COLUMN notice_type VARCHAR(32) NOT NULL DEFAULT 'COMPETITION'
    COMMENT '申报类型：COMPETITION-竞赛, RESEARCH-科研项目, SCHOLARSHIP-奖学金, OTHER-其他';

-- 2. AI解析状态（追踪AI解析进度）
ALTER TABLE competition_notice
ADD COLUMN parse_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
    COMMENT 'AI解析状态：DRAFT-待解析, PARSING-解析中, CONFIRM_PENDING-待确认, PARSE_FAILED-解析失败';

-- 3. 发布状态（控制通知对外可见性）
ALTER TABLE competition_notice
ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
    COMMENT '发布状态：DRAFT-草稿, PUBLISHED-已发布, ARCHIVED-已归档';

-- 新增索引：按发布状态查询
CREATE INDEX idx_notice_publish_status ON competition_notice (publish_status);

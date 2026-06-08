-- V5: Add teacher review columns to project_material
-- 为材料提交模块增加教师审核功能

ALTER TABLE project_material
    ADD COLUMN review_status VARCHAR(20) DEFAULT NULL COMMENT '教师审核状态: NULL-未审核, approved-通过, revision-需修改',
    ADD COLUMN review_comment TEXT DEFAULT NULL COMMENT '教师审核意见/修改建议',
    ADD COLUMN reviewed_by BIGINT DEFAULT NULL COMMENT '审核教师ID',
    ADD COLUMN reviewed_at DATETIME DEFAULT NULL COMMENT '审核时间',
    ADD CONSTRAINT fk_project_material_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user (user_id);

CREATE INDEX idx_project_material_review_status ON project_material (review_status);
CREATE INDEX idx_project_material_reviewed_by ON project_material (reviewed_by);

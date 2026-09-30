-- The versioned material input fingerprint also records project context and all uploaded version IDs.
ALTER TABLE project_ai_check MODIFY COLUMN material_snapshot TEXT NULL
    COMMENT 'Versioned material AI input snapshot; legacy rows contain only uploaded version IDs';

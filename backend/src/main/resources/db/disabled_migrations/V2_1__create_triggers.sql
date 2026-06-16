DROP TRIGGER IF EXISTS trg_project_material_after_insert_refresh_project;
DROP TRIGGER IF EXISTS trg_project_material_after_update_refresh_project;

DELIMITER $$

CREATE TRIGGER trg_project_material_after_insert_refresh_project
AFTER INSERT ON project_material
FOR EACH ROW
BEGIN
    CALL sp_refresh_project_progress(NEW.project_id);
END
$$

CREATE TRIGGER trg_project_material_after_update_refresh_project
AFTER UPDATE ON project_material
FOR EACH ROW
BEGIN
    CALL sp_refresh_project_progress(NEW.project_id);
    IF OLD.project_id <> NEW.project_id THEN
        CALL sp_refresh_project_progress(OLD.project_id);
    END IF;
END
$$

DELIMITER ;

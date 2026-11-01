-- V8: Record which tray each sub-folder lives under (Issue #29)
--
-- A tray's visibility decides who can see everything inside it, so access checks
-- need to find the tray quickly. Sub-folders can't rely on their own visibility
-- column: it is only copied when they are created, and goes stale if the tray changes.
--   - Tray:       root_tray_id IS NULL
--   - Sub-folder: root_tray_id points at the tray at the top of its tree

ALTER TABLE folders
    ADD COLUMN root_tray_id UUID REFERENCES folders(id) ON DELETE CASCADE;

-- Fill in existing sub-folders by walking down from each tray.
WITH RECURSIVE tree AS (
    SELECT id, id AS root_id
    FROM folders
    WHERE parent_folder_id IS NULL
    UNION ALL
    SELECT child.id, tree.root_id
    FROM folders child
    JOIN tree ON child.parent_folder_id = tree.id
)
UPDATE folders
SET root_tray_id = tree.root_id
FROM tree
WHERE folders.id = tree.id
  AND folders.parent_folder_id IS NOT NULL;

-- A sub-folder without a tray would skip the check on its tray's visibility, so forbid it.
ALTER TABLE folders
    ADD CONSTRAINT ck_folders_root_tray
    CHECK ((parent_folder_id IS NULL) = (root_tray_id IS NULL));

CREATE INDEX idx_folders_root_tray ON folders (root_tray_id);

-- El aviso de trueque dice qué se pide y qué se da.

ALTER TABLE timebank_needs
    ADD COLUMN IF NOT EXISTS give_label TEXT,
    ADD COLUMN IF NOT EXISTS give_tag_id UUID REFERENCES skill_tags (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_timebank_needs_give
    ON timebank_needs (give_tag_id)
    WHERE open;

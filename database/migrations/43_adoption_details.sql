-- Extend adoption listings; historical rows and their timestamps stay untouched.
ALTER TABLE animal_listings ADD COLUMN IF NOT EXISTS adoption_details JSONB;
ALTER TABLE animal_listings DROP CONSTRAINT IF EXISTS animal_adoption_details_object;
ALTER TABLE animal_listings ADD CONSTRAINT animal_adoption_details_object
    CHECK (adoption_details IS NULL OR (kind = 'ADOPTION' AND jsonb_typeof(adoption_details) = 'object'));

CREATE TABLE IF NOT EXISTS adoption_tags (
    id TEXT PRIMARY KEY,
    label TEXT NOT NULL,
    tag_key TEXT NOT NULL UNIQUE,
    group_name TEXT NOT NULL CHECK (group_name IN ('SPECIES','SIZE','SEX','VACCINATION','STERILIZATION','OTHER')),
    legacy_value TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS adoption_tag_meaning ON adoption_tags(group_name,legacy_value) WHERE legacy_value <> '';
CREATE UNIQUE INDEX IF NOT EXISTS adoption_tag_label_key ON adoption_tags((translate(lower(regexp_replace(label,'[^[:alnum:]_]','','g')),'áéíóúüñ','aeiouun')));
DROP TRIGGER IF EXISTS trg_adoption_tags_updated ON adoption_tags;
CREATE TRIGGER trg_adoption_tags_updated BEFORE UPDATE ON adoption_tags FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO adoption_tags(id,label,tag_key,group_name,legacy_value,sort_order) VALUES
 ('species-dog','Perros','perros','SPECIES','DOG',10),
 ('species-cat','Gatos','gatos','SPECIES','CAT',20),
 ('species-bird','Aves','aves','SPECIES','BIRD',30),
 ('species-rabbit','Conejos','conejos','SPECIES','RABBIT',40),
 ('species-hamster','Hámsters','hamsters','SPECIES','HAMSTER',50),
 ('species-fish','Peces','peces','SPECIES','FISH',60),
 ('species-turtle','Tortugas','tortugas','SPECIES','TURTLE',70),
 ('species-other','Otros animales','otrosanimales','SPECIES','OTHER',80),
 ('size-small','Chico','chico','SIZE','SMALL',10),
 ('size-medium','Mediano','mediano','SIZE','MEDIUM',20),
 ('size-large','Grande','grande','SIZE','LARGE',30),
 ('sex-female','Hembra','hembra','SEX','HEMBRA',10),
 ('sex-male','Macho','macho','SEX','MACHO',20),
 ('vaccination-all','Vacunados','vacunados','VACCINATION','ALL',10),
 ('vaccination-some','Algunos vacunados','algunosvacunados','VACCINATION','SOME',20),
 ('vaccination-none','Sin vacunas','sinvacunas','VACCINATION','NONE',30),
 ('vaccination-unknown','Vacunación sin informar','vacunacionsininformar','VACCINATION','UNKNOWN',40),
 ('sterilization-all','Castrados','castrados','STERILIZATION','ALL',10),
 ('sterilization-some','Algunos castrados','algunoscastrados','STERILIZATION','SOME',20),
 ('sterilization-none','Sin castrar','sincastrar','STERILIZATION','NONE',30),
 ('sterilization-unknown','Castración sin informar','castracionsininformar','STERILIZATION','UNKNOWN',40)
ON CONFLICT (id) DO NOTHING;

-- Tag genérico para abrir un chat sin trueque de habilidad.
INSERT INTO skill_tags (slug, label)
VALUES ('mensaje', 'Mensaje')
ON CONFLICT (slug) DO NOTHING;

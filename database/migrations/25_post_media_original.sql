-- El original queda solo para volver a encuadrar. `url` pasa a ser el JPEG 16:9 que leen las pantallas.
ALTER TABLE post_media ADD COLUMN IF NOT EXISTS original_url TEXT;

COMMENT ON COLUMN post_media.original_url IS 'Archivo original del editor. Las pantallas de lectura usan url, que es el recorte.';

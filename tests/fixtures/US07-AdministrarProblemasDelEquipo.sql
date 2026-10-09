CREATE OR REPLACE PROCEDURE sp_buscar_problemas(
    IN p_texto TEXT,
    OUT p_resultado JSONB
)
LANGUAGE plpgsql AS $$
BEGIN
SELECT COALESCE(jsonb_agg(to_jsonb(q) ORDER BY q.titulo), '[]'::jsonb)
INTO p_resultado
FROM (
         SELECT p.id, p.codigo_origen, p.titulo, p.plataforma_origen,
                p.url_problema, p.dificultad_rating,
                p.limite_tiempo_ms, p.limite_memoria_mb
         FROM problema p
         WHERE p_texto IS NULL OR p_texto = ''
            OR p.titulo ILIKE '%' || p_texto || '%'
              OR p.codigo_origen ILIKE '%' || p_texto || '%'
     ) q;
END; $$;
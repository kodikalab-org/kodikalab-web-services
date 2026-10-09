CREATE OR REPLACE PROCEDURE sp_listar_problemas_grupo(
    IN p_grupo_id INTEGER,
    OUT p_resultado JSONB
)
LANGUAGE plpgsql AS $$
BEGIN
SELECT COALESCE(jsonb_agg(to_jsonb(q) ORDER BY q.fecha_inicio DESC, q.orden_letra), '[]'::jsonb)
INTO p_resultado
FROM (
         SELECT cp.id AS competencia_problema_id, cp.orden_letra, cp.puntaje,
                p.id AS problema_id, p.titulo, p.codigo_origen, p.url_problema,
                p.dificultad_rating, c.id AS competencia_id, c.nombre_evento,
                c.estado AS estado_competencia, c.fecha_inicio
         FROM competencia_problema cp
                  JOIN problema p ON p.id = cp.problema_id
                  JOIN competencia c ON c.id = cp.competencia_id
         WHERE c.grupo_id = p_grupo_id
     ) q;
END; $$;
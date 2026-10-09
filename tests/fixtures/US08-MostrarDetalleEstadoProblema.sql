CREATE OR REPLACE PROCEDURE sp_estado_problemas_integrante(
    IN p_membership_id INTEGER,
    OUT p_resultado JSONB
)
LANGUAGE plpgsql AS $$
BEGIN
    SELECT COALESCE(jsonb_agg(to_jsonb(q) ORDER BY q.ultimo_intento DESC), '[]'::jsonb)
      INTO p_resultado
      FROM (
          SELECT p.id AS problema_id, p.titulo, cp.id AS competencia_problema_id,
                 MAX(r.fecha_envio) AS ultimo_intento,
                 CASE WHEN BOOL_OR(r.veredicto = 'ACCEPTED') THEN 'ACCEPTED'
                      ELSE (ARRAY_AGG(r.veredicto ORDER BY r.fecha_envio DESC))[1]
                 END AS estado
            FROM resolucion_problema r
            JOIN competencia_problema cp ON cp.id = r.competencia_problema_id
            JOIN problema p ON p.id = cp.problema_id
           WHERE r.practicante_grupo_id = p_membership_id
           GROUP BY p.id, p.titulo, cp.id
      ) q;
END; $$;
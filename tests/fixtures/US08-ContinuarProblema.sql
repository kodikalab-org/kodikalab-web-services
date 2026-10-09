CREATE OR REPLACE PROCEDURE sp_continuar_practica(
    IN p_membership_id INTEGER,
    IN p_problema_id INTEGER,
    OUT p_resultado JSONB
)
LANGUAGE plpgsql AS $$
BEGIN
    SELECT COALESCE(jsonb_agg(to_jsonb(q) ORDER BY q.fecha_envio DESC), '[]'::jsonb)
      INTO p_resultado
      FROM (
          SELECT r.id AS resolucion_id, r.veredicto, r.lenguaje, r.fecha_envio,
                 r.url_evidencia, cp.id AS competencia_problema_id,
                 p.id AS problema_id, p.titulo, p.url_problema
            FROM resolucion_problema r
            JOIN competencia_problema cp ON cp.id = r.competencia_problema_id
            JOIN problema p ON p.id = cp.problema_id
           WHERE r.practicante_grupo_id = p_membership_id
             AND p.id = p_problema_id
      ) q;
END; $$;
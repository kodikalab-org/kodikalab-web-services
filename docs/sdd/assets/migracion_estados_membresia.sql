
-- KodikaLab - Migracion de estados de membresia
-- US05 y US06
--
-- Amplia los estados permitidos en practicante_grupo.
-- No crea tablas ni modifica los registros existentes.
--
-- Nota: este script conserva los estados de US05/US06. Para US04 (borrador de
-- grupo) y la aclaracion al postulante use `migracion_borrador_aclaracion.sql`,
-- que ademas agrega las columnas de aclaracion.
--
-- Ejecutar solamente sobre una base de datos existente,
-- despues de realizar un respaldo.

BEGIN;

ALTER TABLE public.practicante_grupo
DROP CONSTRAINT IF EXISTS practicante_grupo_estado_check;

ALTER TABLE public.practicante_grupo
    ADD CONSTRAINT practicante_grupo_estado_check
        CHECK (
            estado IN (
                       'PENDIENTE',
                       'ACLARACION_SOLICITADA',
                       'ACTIVO',
                       'RECHAZADO',
                       'RETIRADO',
                       'EXPULSADO'
                )
            );

COMMIT;

-- Verificar la restriccion actualizada.
SELECT
    conname AS nombre_restriccion,
    pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE conrelid = 'public.practicante_grupo'::regclass
  AND conname = 'practicante_grupo_estado_check';

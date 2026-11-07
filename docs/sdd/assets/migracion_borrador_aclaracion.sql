-- KodikaLab - Migracion de borrador de grupo y aclaracion al postulante
-- US04: guardar borrador del grupo
-- US06: pedir aclaracion al postulante
--
-- Amplia los estados permitidos en grupo_estudio.estado (BORRADOR) y en
-- practicante_grupo.estado (ACLARACION_SOLICITADA), agrega las columnas donde
-- se guarda la pregunta del coach y la respuesta del postulante, amplia
-- practicante_grupo.estado a VARCHAR(30) porque ACLARACION_SOLICITADA mide 22,
-- y permite que nivel_esperado y cupo_maximo queden nulos mientras el grupo es
-- un borrador.
--
-- No crea tablas ni modifica los registros existentes: las columnas nuevas
-- son NULL y los grupos existentes conservan su estado y sus valores.
--
-- Ejecutar solamente sobre una base de datos existente,
-- despues de realizar un respaldo.

BEGIN;

-- US04: grupo_estudio.estado admite BORRADOR.
ALTER TABLE public.grupo_estudio
    DROP CONSTRAINT IF EXISTS grupo_estudio_estado_check;

ALTER TABLE public.grupo_estudio
    ADD CONSTRAINT grupo_estudio_estado_check
        CHECK (
            estado IN (
                       'BORRADOR',
                       'ACTIVO',
                       'ARCHIVADO'
                )
            );

-- US04: un borrador solo exige el nombre; el nivel y el cupo se completan
-- antes de publicar, por lo que ambos dejan de ser NOT NULL.
ALTER TABLE public.grupo_estudio
    ALTER COLUMN nivel_esperado DROP NOT NULL;

ALTER TABLE public.grupo_estudio
    ALTER COLUMN cupo_maximo DROP NOT NULL;

-- US06: practicante_grupo.estado admite ACLARACION_SOLICITADA.
-- El ERD original declaraba VARCHAR(20) y ese valor mide 22 caracteres:
-- se amplia la columna antes de redefinir la restriccion.
ALTER TABLE public.practicante_grupo
    ALTER COLUMN estado TYPE VARCHAR(30);

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

-- US06: datos de la aclaracion, en la misma fila de practicante_grupo.
ALTER TABLE public.practicante_grupo
    ADD COLUMN IF NOT EXISTS aclaracion_solicitada VARCHAR(500);

ALTER TABLE public.practicante_grupo
    ADD COLUMN IF NOT EXISTS fecha_aclaracion TIMESTAMP WITH TIME ZONE;

ALTER TABLE public.practicante_grupo
    ADD COLUMN IF NOT EXISTS aclaracion_respuesta VARCHAR(500);

ALTER TABLE public.practicante_grupo
    ADD COLUMN IF NOT EXISTS fecha_respuesta_aclaracion TIMESTAMP WITH TIME ZONE;

COMMIT;

-- Verificar las restricciones actualizadas.
SELECT
    conname AS nombre_restriccion,
    pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE conrelid IN (
                   'public.grupo_estudio'::regclass,
                   'public.practicante_grupo'::regclass
    )
  AND conname IN (
                  'grupo_estudio_estado_check',
                  'practicante_grupo_estado_check'
    );

-- Verificar las columnas de aclaracion.
SELECT
    column_name,
    data_type,
    character_maximum_length,
    is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'practicante_grupo'
  AND column_name IN (
                      'aclaracion_solicitada',
                      'fecha_aclaracion',
                      'aclaracion_respuesta',
                      'fecha_respuesta_aclaracion'
    )
ORDER BY ordinal_position;

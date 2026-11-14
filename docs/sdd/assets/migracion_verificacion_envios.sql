-- KodikaLab - Migracion de verificacion de envios con Codeforces (solo lectura)
--
-- Agrega a resolucion_problema las columnas para registrar el envio real que KodikaLab lee de
-- Codeforces, amplia los veredictos permitidos y evita importar dos veces el mismo envio.
-- KodikaLab nunca envia soluciones: solo lee (GET) los envios del handle del practicante.
--
--  * origen: PLATAFORMA (importado de Codeforces) o MANUAL (autodeclarado). Las filas existentes quedan MANUAL.
--  * id_envio_externo: id del envio en Codeforces.
--  * pruebas_superadas, nombre_archivo, codigo_fuente, sha256: datos del envio y del archivo opcional adjunto.
--  * comparacion_archivo: COINCIDE / DIFERENTE / NO_COMPARADO (la comparacion se implementa en otra tarea).
--  * Indice unico parcial (competencia_problema_id, practicante_grupo_id, id_envio_externo).
--  * CHECK de veredicto ampliado con COMPILATION_ERROR, RUNTIME_ERROR, EN_EVALUACION y OTRO.
--
-- Idempotente: se puede ejecutar mas de una vez. No borra ni modifica veredictos existentes.
-- Ejecutar solamente sobre una base de datos existente (PostgreSQL), despues de realizar un respaldo.

BEGIN;

ALTER TABLE public.resolucion_problema
    ADD COLUMN IF NOT EXISTS origen              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS id_envio_externo    BIGINT,
    ADD COLUMN IF NOT EXISTS pruebas_superadas   INTEGER,
    ADD COLUMN IF NOT EXISTS nombre_archivo      VARCHAR(255),
    ADD COLUMN IF NOT EXISTS codigo_fuente       TEXT,
    ADD COLUMN IF NOT EXISTS sha256              VARCHAR(64),
    ADD COLUMN IF NOT EXISTS comparacion_archivo VARCHAR(20);

-- Las resoluciones existentes eran autodeclaradas.
UPDATE public.resolucion_problema
SET origen = 'MANUAL'
WHERE origen IS NULL;

-- Veredictos: Hibernate crea resolucion_problema_veredicto_check; se reemplaza por la lista ampliada.
ALTER TABLE public.resolucion_problema
    ALTER COLUMN veredicto TYPE VARCHAR(30);

ALTER TABLE public.resolucion_problema
    DROP CONSTRAINT IF EXISTS resolucion_problema_veredicto_check;

ALTER TABLE public.resolucion_problema
    ADD CONSTRAINT resolucion_problema_veredicto_check
        CHECK (
            veredicto IN (
                          'PENDIENTE',
                          'ACCEPTED',
                          'WRONG_ANSWER',
                          'TLE',
                          'MLE',
                          'ERROR',
                          'COMPILATION_ERROR',
                          'RUNTIME_ERROR',
                          'EN_EVALUACION',
                          'OTRO'
                )
            );

ALTER TABLE public.resolucion_problema
    DROP CONSTRAINT IF EXISTS resolucion_problema_origen_check;

ALTER TABLE public.resolucion_problema
    ADD CONSTRAINT resolucion_problema_origen_check
        CHECK (origen IS NULL OR origen IN ('PLATAFORMA', 'MANUAL'));

ALTER TABLE public.resolucion_problema
    DROP CONSTRAINT IF EXISTS resolucion_problema_comparacion_archivo_check;

ALTER TABLE public.resolucion_problema
    ADD CONSTRAINT resolucion_problema_comparacion_archivo_check
        CHECK (comparacion_archivo IS NULL OR comparacion_archivo IN ('COINCIDE', 'DIFERENTE', 'NO_COMPARADO'));

-- Un mismo envio externo no se importa dos veces para el mismo problema de competencia y membresia.
CREATE UNIQUE INDEX IF NOT EXISTS uq_resolucion_problema_envio_externo
    ON public.resolucion_problema (competencia_problema_id, practicante_grupo_id, id_envio_externo)
    WHERE id_envio_externo IS NOT NULL;

COMMIT;

-- Verificar columnas, restricciones e indice.
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'resolucion_problema'
ORDER BY ordinal_position;

SELECT conname AS nombre_restriccion, pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE conrelid = 'public.resolucion_problema'::regclass
  AND contype = 'c';

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'resolucion_problema';

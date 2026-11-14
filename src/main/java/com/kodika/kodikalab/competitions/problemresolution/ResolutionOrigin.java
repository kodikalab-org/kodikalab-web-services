package com.kodika.kodikalab.competitions.problemresolution;

/** Valores de resolucion_problema.origen: cómo llegó el intento a KodikaLab. */
public enum ResolutionOrigin {
    /** Importado por lectura (GET) de los envíos del practicante en la plataforma de origen. */
    PLATAFORMA,
    /** Autodeclarado por el practicante; las filas anteriores a la verificación se consideran MANUAL. */
    MANUAL
}

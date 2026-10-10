package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.AssignProblemsRequest;
import com.kodika.kodikalab.assignments.dto.AssignProblemsResponse;

/** US-07: asignación de problemas del catálogo a las competencias de un equipo. */
public interface AssignmentService {
    /**
     * El coach responsable del equipo asigna uno o más problemas a una de sus competencias. Es todo o nada: si algún
     * problema no existe, ya está asignado o la letra está en uso, no se registra ninguno y se informa la causa por
     * campo. Los problemas quedan asignados al equipo completo; el modelo no registra destinatarios.
     */
    AssignProblemsResponse assign(AssignProblemsRequest request);
}

package com.kodika.kodikalab.agent.core;

import com.kodika.kodikalab.users.Role;
import java.util.List;
import java.util.Set;

/**
 * Ficha de un agente. El router elige por {@code description}; el código, nunca el modelo, comprueba el rol antes de
 * ejecutarlo. {@code tools} son los objetos con métodos {@code @Tool} que ese agente puede pedir.
 *
 * @param name          identificador estable que devuelve el router
 * @param description   cuándo corresponde usar el agente (la lee el router)
 * @param allowedRoles  roles que pueden usarlo; no puede estar vacío
 * @param usesKnowledge si responde con los documentos (RAG)
 * @param tools         objetos con las tools del agente; puede estar vacío
 */
public record AgentDefinition(String name, String description, Set<Role> allowedRoles, boolean usesKnowledge,
                              List<Object> tools) {

    public AgentDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("An agent needs a name");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Agent " + name + " needs a description for the router");
        }
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            throw new IllegalArgumentException("Agent " + name + " must state which roles may use it");
        }
        allowedRoles = Set.copyOf(allowedRoles);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public boolean isAllowedFor(Role role) {
        return role != null && allowedRoles.contains(role);
    }
}

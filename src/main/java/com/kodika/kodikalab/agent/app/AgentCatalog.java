package com.kodika.kodikalab.agent.app;

import com.kodika.kodikalab.agent.app.tools.InsightTools;
import com.kodika.kodikalab.agent.core.AgentDefinition;
import com.kodika.kodikalab.users.Role;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Los 4 agentes de KodikaLab. El router lee sus descripciones. Para activar uno, su responsable le agrega tools (el
 * agente {@code info} necesitará además que el orquestador active el RAG). Sin tools responde que aún no está
 * disponible.
 */
@Configuration
public class AgentCatalog {

    @Bean
    public AgentDefinition insightsAgent(InsightTools tools) {
        return new AgentDefinition("insights",
                "Rendimiento de un equipo: temas débiles, recomendación de problemas para practicar o asignar y "
                        + "resumen de preparación antes de una competencia.",
                Set.of(Role.COACH), false, List.of(tools));
    }

    @Bean
    public AgentDefinition teamsAgent() {
        return new AgentDefinition("teams",
                "Buscar grupos de entrenamiento según disponibilidad, nivel y preferencias.",
                Set.of(Role.PRACTICANTE), false, List.of());
    }

    @Bean
    public AgentDefinition actionsAgent() {
        return new AgentDefinition("actions",
                "Ejecutar una acción en la plataforma: unirse a un grupo, crear un grupo o registrar un resultado.",
                Set.of(Role.COACH, Role.PRACTICANTE), false, List.of());
    }

    @Bean
    public AgentDefinition infoAgent() {
        return new AgentDefinition("info",
                "Preguntas sobre las políticas de KodikaLab y cómo usar la plataforma.",
                Set.of(Role.COACH, Role.PRACTICANTE), true, List.of());
    }
}

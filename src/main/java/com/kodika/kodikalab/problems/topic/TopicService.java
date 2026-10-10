package com.kodika.kodikalab.problems.topic;

import java.util.Collection;
import java.util.List;

/** Servicio de temas algorítmicos (tema). */
public interface TopicService {
    /**
     * Devuelve los temas con esos nombres, sin distinguir mayúsculas, en el mismo orden, creando los que no
     * existen. Los nombres deben llegar recortados y no vacíos.
     */
    List<Topic> findOrCreateAll(Collection<String> names);
}

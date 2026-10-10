package com.kodika.kodikalab.problems.topic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicServiceImpl implements TopicService {
    private final TopicRepository topicRepository;

    public TopicServiceImpl(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    @Override
    @Transactional
    public List<Topic> findOrCreateAll(Collection<String> names) {
        if (names.isEmpty()) {
            return List.of();
        }
        // El catálogo de temas es pequeño; comparar en Java evita depender de la intercalación de la base.
        Map<String, Topic> known = new HashMap<>();
        for (Topic topic : topicRepository.findAll()) {
            known.put(key(topic.getName()), topic);
        }
        List<Topic> result = new ArrayList<>();
        List<Topic> created = new ArrayList<>();
        for (String name : names) {
            Topic topic = known.get(key(name));
            if (topic == null) {
                topic = new Topic();
                topic.setName(name.strip());
                known.put(key(name), topic);
                created.add(topic);
            }
            result.add(topic);
        }
        topicRepository.saveAll(created);
        return result;
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}

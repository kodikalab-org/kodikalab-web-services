package com.kodika.kodikalab.problems.topic;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicServiceTests {
    TopicRepository repository;
    TopicService service;

    @BeforeEach
    void setUp() {
        repository = mock(TopicRepository.class);
        service = new TopicServiceImpl(repository);
    }

    @Test
    void noNamesDoNotTouchTheRepository() {
        assertThat(service.findOrCreateAll(List.of())).isEmpty();
        verify(repository, never()).findAll();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void existingTopicsAreReusedIgnoringCaseAndKeepingTheirStoredName() {
        Topic graphs = topic(1, "Grafos");
        when(repository.findAll()).thenReturn(List.of(graphs, topic(2, "DP")));

        List<Topic> result = service.findOrCreateAll(List.of("grafos", "DP"));

        assertThat(result).extracting(Topic::getId).containsExactly(1, 2);
        assertThat(result.get(0).getName()).isEqualTo("Grafos");
        ArgumentCaptor<List<Topic>> created = captor();
        verify(repository).saveAll(created.capture());
        assertThat(created.getValue()).isEmpty();
    }

    @Test
    void missingTopicsAreCreatedOnceAndReturnedInRequestOrder() {
        when(repository.findAll()).thenReturn(List.of(topic(2, "DP")));

        List<Topic> result = service.findOrCreateAll(List.of("Árboles", "DP", "Geometría"));

        assertThat(result).extracting(Topic::getName).containsExactly("Árboles", "DP", "Geometría");
        assertThat(result.get(1).getId()).isEqualTo(2);
        ArgumentCaptor<List<Topic>> created = captor();
        verify(repository).saveAll(created.capture());
        assertThat(created.getValue()).extracting(Topic::getName).containsExactly("Árboles", "Geometría");
    }

    @Test
    void repeatedNamesInTheSameCallShareOneNewTopic() {
        when(repository.findAll()).thenReturn(List.of());

        List<Topic> result = service.findOrCreateAll(List.of("Grafos", "GRAFOS"));

        assertThat(result.get(0)).isSameAs(result.get(1));
        ArgumentCaptor<List<Topic>> created = captor();
        verify(repository).saveAll(created.capture());
        assertThat(created.getValue()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Topic>> captor() {
        return ArgumentCaptor.forClass((Class<List<Topic>>) (Class<?>) List.class);
    }

    private static Topic topic(int id, String name) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName(name);
        return topic;
    }
}

package com.kodika.kodikalab.problems.problemtopic;

import com.kodika.kodikalab.problems.problem.Problem;
import com.kodika.kodikalab.problems.topic.Topic;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Tabla {@code problema_tema}: relación N:M; la PK compuesta impide repetir un tema en el mismo problema. */
@Entity
@Table(name = "problema_tema", indexes = @Index(name = "idx_problema_tema_tema_id", columnList = "tema_id"))
@Getter
@Setter
public class ProblemTopic {
    @EmbeddedId
    private ProblemTopicId id = new ProblemTopicId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("problemId")
    @JoinColumn(name = "problema_id", nullable = false)
    private Problem problem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("topicId")
    @JoinColumn(name = "tema_id", nullable = false)
    private Topic topic;
}

package com.kodika.kodikalab.problems.problemtopic;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PK compuesta de {@code problema_tema}: (problema_id, tema_id). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ProblemTopicId implements Serializable {
    @Column(name = "problema_id")
    private Integer problemId;

    @Column(name = "tema_id")
    private Integer topicId;

    public ProblemTopicId(Integer problemId, Integer topicId) {
        this.problemId = problemId;
        this.topicId = topicId;
    }
}

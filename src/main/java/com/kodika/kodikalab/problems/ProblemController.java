package com.kodika.kodikalab.problems;

import com.kodika.kodikalab.problems.material.MaterialService;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopicService;
import com.kodika.kodikalab.problems.topic.TopicService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plantilla del controller de problems en {@code /api/problems}. Aún no expone endpoints:
 * agregar cada {@code @GetMapping}/{@code @PostMapping}... con su historia y su contrato en
 * {@code docs/sdd/03-api-contracts.md}, delegando en el servicio de la entidad.
 */
@RestController
@RequestMapping("/problems")
public class ProblemController {
    private final ProblemService problemService;
    private final TopicService topicService;
    private final ProblemTopicService problemTopicService;
    private final MaterialService materialService;

    public ProblemController(ProblemService problemService,
                             TopicService topicService,
                             ProblemTopicService problemTopicService,
                             MaterialService materialService) {
        this.problemService = problemService;
        this.topicService = topicService;
        this.problemTopicService = problemTopicService;
        this.materialService = materialService;
    }
}

package com.kodika.kodikalab.problems;

import com.kodika.kodikalab.problems.material.MaterialService;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.CreateProblemRequest;
import com.kodika.kodikalab.problems.problem.dto.ProblemPageResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;
import com.kodika.kodikalab.problems.problem.dto.ProblemSearch;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopicService;
import com.kodika.kodikalab.problems.topic.TopicService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller de problems en {@code /api/problems}: catálogo de problemas (registro y búsqueda). Las rutas de
 * asignación ({@code /problems/assign}, {@code /problems/assigned}) viven en el módulo {@code assignments}.
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemResponse create(@RequestBody CreateProblemRequest request) {
        return problemService.create(request);
    }

    @GetMapping
    public ProblemPageResponse search(@RequestParam(name = "q", required = false) String query,
                                      @RequestParam(required = false) Integer topicId,
                                      @RequestParam(required = false) String difficulty,
                                      @RequestParam(required = false) SourcePlatform platform,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return problemService.search(new ProblemSearch(query, topicId, difficulty, platform, page, size));
    }
}

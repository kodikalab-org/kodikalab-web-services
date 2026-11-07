package com.kodika.kodikalab.problems.codeforces.dto;

import com.kodika.kodikalab.problems.problem.dto.ProblemResponse;

/** {@code created} es {@code false} cuando el problema ya estaba en el catálogo (no se consultó Codeforces). */
public record ImportCodeforcesProblemResponse(boolean created, ProblemResponse problem) {
}

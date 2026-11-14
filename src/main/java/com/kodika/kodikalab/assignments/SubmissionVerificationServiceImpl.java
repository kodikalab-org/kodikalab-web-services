package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.PlatformSubmissionsResponse;
import com.kodika.kodikalab.assignments.dto.SubmissionFile;
import com.kodika.kodikalab.assignments.dto.VerificationResponse;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.competition.CompetitionStatus;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblemService;
import com.kodika.kodikalab.competitions.competitionproblem.dto.AssignmentView;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolutionService;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.competitions.problemresolution.dto.MemberAttempt;
import com.kodika.kodikalab.competitions.problemresolution.dto.PlatformImportResult;
import com.kodika.kodikalab.competitions.problemresolution.dto.PlatformSubmissionData;
import com.kodika.kodikalab.competitions.problemresolution.dto.SubmittedFile;
import com.kodika.kodikalab.problems.codeforces.CodeforcesProblemCode;
import com.kodika.kodikalab.problems.problem.ProblemService;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import com.kodika.kodikalab.problems.problem.dto.ProblemSummary;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfileService;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesClient;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesSubmission;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import com.kodika.kodikalab.users.UserStatus;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Orquesta la verificación: autoriza, lee de Codeforces SIN transacción abierta (la llamada de red nunca retiene una
 * conexión de base de datos) y delega el guardado idempotente en {@link ProblemResolutionService}.
 */
@Service
public class SubmissionVerificationServiceImpl implements SubmissionVerificationService {
    static final long MAX_FILE_BYTES = 64 * 1024;
    static final Set<String> EXTENSIONS = Set.of("cpp", "c", "cc", "py", "java", "kt", "rs", "js", "go");
    static final String UNLINKED = "Vincula tu usuario de Codeforces en tu perfil para verificar";
    static final String ONLY_CODEFORCES = "La verificación automática solo está disponible para Codeforces";
    private static final String VALIDATION_MESSAGE = "Los datos de la verificación deben corregirse";

    private final CurrentUserResolver currentUserResolver;
    private final StudyGroupService groupService;
    private final GroupMembershipService membershipService;
    private final CompetitionProblemService competitionProblemService;
    private final ProblemService problemService;
    private final ProblemResolutionService resolutionService;
    private final PractitionerProfileService profileService;
    private final CodeforcesClient codeforces;

    public SubmissionVerificationServiceImpl(CurrentUserResolver currentUserResolver,
                                             StudyGroupService groupService,
                                             GroupMembershipService membershipService,
                                             CompetitionProblemService competitionProblemService,
                                             ProblemService problemService,
                                             ProblemResolutionService resolutionService,
                                             PractitionerProfileService profileService,
                                             CodeforcesClient codeforces) {
        this.currentUserResolver = currentUserResolver;
        this.groupService = groupService;
        this.membershipService = membershipService;
        this.competitionProblemService = competitionProblemService;
        this.problemService = problemService;
        this.resolutionService = resolutionService;
        this.profileService = profileService;
        this.codeforces = codeforces;
    }

    /** Contexto ya autorizado de una solicitud. */
    private record Context(User user, AssignmentView view, CodeforcesProblemCode problem, String handle) {
    }

    @Override
    public VerificationResponse verify(Integer competitionProblemId, SubmissionFile file, Integer count) {
        int window = window(count);
        Context context = authorize(competitionProblemId, true);
        SubmittedFile validFile = validate(file);

        List<CodeforcesSubmission> recent = fetch(context.handle(), window);
        List<CodeforcesSubmission> mine = recent.stream().filter(s -> matches(s, context.problem()))
                .filter(s -> s.id() != null)
                .sorted(Comparator.comparingLong(CodeforcesSubmission::creationTimeSeconds).reversed()
                        .thenComparing(CodeforcesSubmission::id, Comparator.reverseOrder()))
                .toList();

        OffsetDateTime startsAt = context.view().startsAt();
        List<PlatformSubmissionData> toImport = new ArrayList<>();
        for (CodeforcesSubmission submission : mine) {
            PlatformSubmissionData data = data(submission);
            if (!isBefore(data, startsAt)) {
                toImport.add(data);
            }
        }
        Long latestId = toImport.isEmpty() ? null : toImport.get(0).externalId();
        PlatformImportResult result = resolutionService.importPlatformSubmissions(context.view().teamId(),
                competitionProblemId, toImport, validFile, latestId);

        List<VerificationResponse.Submission> items = new ArrayList<>();
        for (CodeforcesSubmission submission : mine) {
            PlatformSubmissionData data = data(submission);
            boolean before = isBefore(data, startsAt);
            items.add(new VerificationResponse.Submission(data.externalId(), data.verdict(), data.language(),
                    data.executionTimeMs(), data.memoryUsedKb(), data.passedTests(), data.submittedAt(), data.url(),
                    result.imported().contains(data.externalId()), before));
        }
        AssignmentStatus status = AssignedProblemsServiceImpl.statusOf(ownAttempts(context));
        return new VerificationResponse(competitionProblemId, !mine.isEmpty(), status, result.fileStored(), items);
    }

    @Override
    public PlatformSubmissionsResponse platformSubmissions(Integer competitionProblemId, Integer count) {
        int window = window(count);
        Context context = authorize(competitionProblemId, false);
        List<PlatformSubmissionsResponse.Submission> items = fetch(context.handle(), window).stream().map(s -> {
            PlatformSubmissionData data = data(s);
            return new PlatformSubmissionsResponse.Submission(s.id(), s.contestId(), s.index(), data.verdict(),
                    s.verdict(), data.language(), data.executionTimeMs(), data.memoryUsedKb(), data.passedTests(),
                    data.submittedAt(), data.url(), matches(s, context.problem()));
        }).toList();
        return new PlatformSubmissionsResponse(competitionProblemId, context.handle(), window, items);
    }

    private List<MemberAttempt> ownAttempts(Context context) {
        Integer membershipId = membershipService.findMembersByTeamId(context.view().teamId()).stream()
                .filter(member -> member != null && context.user().getId().equals(member.userId())
                        && member.status() == MembershipStatus.ACTIVO)
                .map(member -> member.membershipId()).findFirst().orElseThrow(this::notMember);
        return resolutionService.findAttemptsByMembershipId(membershipId).stream()
                .filter(attempt -> context.view().competitionProblemId().equals(attempt.competitionProblemId()))
                .toList();
    }

    private Context authorize(Integer competitionProblemId, boolean requireStarted) {
        if (competitionProblemId == null || competitionProblemId <= 0) {
            throw new FieldValidationException(VALIDATION_MESSAGE, Map.of("competitionProblemId",
                    "La asignación debe ser un entero positivo"));
        }
        User user = currentUserResolver.currentUser();
        if (user.getStatus() != UserStatus.ACTIVO) {
            throw new ForbiddenException("La cuenta no está activa");
        }
        if (user.getRole() != Role.PRACTICANTE) {
            throw new ForbiddenException("Solo un practicante puede verificar sus envíos");
        }
        AssignmentView view = competitionProblemService.findViewById(competitionProblemId)
                .orElseThrow(() -> new NotFoundException("La asignación no existe"));
        groupService.findSummaryById(view.teamId()).orElseThrow(() -> new NotFoundException("El equipo no existe"));
        boolean member = membershipService.findMembersByTeamId(view.teamId()).stream()
                .anyMatch(m -> m != null && user.getId().equals(m.userId()) && m.status() == MembershipStatus.ACTIVO);
        if (!member) {
            throw notMember();
        }
        ProblemSummary problem = problemService.findSummariesByIds(Set.of(view.problemId())).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("El problema de la asignación no existe"));
        if (problem.sourcePlatform() != SourcePlatform.CODEFORCES) {
            throw new ConflictException(ONLY_CODEFORCES);
        }
        if (requireStarted && view.competitionStatus() == CompetitionStatus.PROGRAMADA) {
            throw new ConflictException(
                    "La competencia aún no ha comenzado: el coach debe iniciarla antes de verificar envíos");
        }
        String handle = handleOf(user);
        return new Context(user, view, code(problem), handle);
    }

    private String handleOf(User user) {
        String handle;
        try {
            handle = profileService.requirePractitionerProfile(user.getId()).getCodeforcesHandle();
        } catch (NotFoundException exception) {
            handle = null;
        }
        if (handle == null || handle.isBlank()) {
            throw new ConflictException(UNLINKED);
        }
        return handle.strip();
    }

    private static CodeforcesProblemCode code(ProblemSummary problem) {
        for (String candidate : new String[] {problem.sourceCode(), problem.url()}) {
            try {
                return CodeforcesProblemCode.parse(candidate);
            } catch (FieldValidationException ignored) {
                // se prueba con la siguiente fuente
            }
        }
        throw new ConflictException("No se pudo identificar el problema de Codeforces de esta asignación");
    }

    /** Solo lectura contra Codeforces; sin handle válido se informa como conflicto, no como 404 de la asignación. */
    private List<CodeforcesSubmission> fetch(String handle, int count) {
        try {
            return codeforces.recentSubmissions(handle, count);
        } catch (NotFoundException exception) {
            throw new ConflictException(
                    "El usuario de Codeforces vinculado no existe; corrige tu usuario en tu perfil para verificar");
        }
    }

    private static boolean matches(CodeforcesSubmission submission, CodeforcesProblemCode problem) {
        return submission.contestId() != null && submission.contestId() == problem.contestId()
                && submission.index() != null && problem.index().equalsIgnoreCase(submission.index());
    }

    private static boolean isBefore(PlatformSubmissionData data, OffsetDateTime startsAt) {
        return startsAt != null && data.submittedAt().isBefore(startsAt);
    }

    static PlatformSubmissionData data(CodeforcesSubmission submission) {
        OffsetDateTime at = Instant.ofEpochSecond(submission.creationTimeSeconds()).atOffset(ZoneOffset.UTC);
        String url = submission.id() == null || submission.contestId() == null ? null
                : "https://codeforces.com/contest/" + submission.contestId() + "/submission/" + submission.id();
        Integer memoryKb = submission.memoryConsumedBytes() == null ? null
                : (int) Math.min(Integer.MAX_VALUE, submission.memoryConsumedBytes() / 1024);
        return new PlatformSubmissionData(submission.id(), Verdict.fromCodeforces(submission.verdict()),
                submission.programmingLanguage(), submission.timeConsumedMillis(), memoryKb,
                submission.passedTestCount(), at, url);
    }

    private static int window(Integer count) {
        if (count == null) {
            return DEFAULT_COUNT;
        }
        if (count < 1 || count > MAX_COUNT) {
            throw new FieldValidationException(VALIDATION_MESSAGE,
                    Map.of("count", "Debe estar entre 1 y " + MAX_COUNT));
        }
        return count;
    }

    /** Extensión permitida, máximo 64 kB y UTF-8 estricto; {@code null} si no se adjuntó archivo. */
    static SubmittedFile validate(SubmissionFile file) {
        if (file == null) {
            return null;
        }
        String name = file.fileName() == null ? "" : file.fileName().strip();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > 255 || !EXTENSIONS.contains(extension)) {
            throw invalidFile("Extensión no permitida; use una de: .cpp .c .cc .py .java .kt .rs .js .go");
        }
        byte[] content = file.content() == null ? new byte[0] : file.content();
        if (content.length == 0) {
            throw invalidFile("El archivo está vacío");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw invalidFile("El archivo supera el máximo de 64 kB");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            throw invalidFile("El archivo debe estar codificado en UTF-8");
        }
        if (text.indexOf('\0') >= 0) {
            throw invalidFile("El archivo debe ser texto");
        }
        return new SubmittedFile(name, text, sha256(content));
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static FieldValidationException invalidFile(String detail) {
        return new FieldValidationException("El archivo adjunto no es válido", Map.of("file", detail));
    }

    private ForbiddenException notMember() {
        return new ForbiddenException("No pertenece a este equipo");
    }
}

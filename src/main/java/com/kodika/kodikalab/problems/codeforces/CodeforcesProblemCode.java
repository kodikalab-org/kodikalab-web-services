package com.kodika.kodikalab.problems.codeforces;

import com.kodika.kodikalab.common.exception.FieldValidationException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Identificador de un problema de Codeforces: concurso e índice (p. ej. {@code 1850} y {@code A}).
 *
 * <p>Se acepta el código corto ({@code 1850A}, {@code 1850a}, {@code CF-1850A}, {@code 1850B2}) o la URL del problema
 * ({@code /problemset/problem/1850/A} o {@code /contest/1850/problem/A}). El índice se normaliza a mayúsculas. Los
 * concursos de gimnasio (identificador de 6 cifras) no están en el problemset público y se rechazan.
 */
public record CodeforcesProblemCode(int contestId, String index) {
    static final int MAX_CONTEST_ID = 99_999;
    private static final String INDEX = "([A-Za-z][0-9]?)";
    private static final Pattern SHORT = Pattern.compile("^(?:CF-?)?(\\d{1,6})" + INDEX + "$", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("^https?://(?:www\\.)?codeforces\\.com/(?:"
            + "problemset/problem/(\\d{1,6})/" + INDEX + "|contest/(\\d{1,6})/problem/" + INDEX
            + ")/?(?:[?#].*)?$", Pattern.CASE_INSENSITIVE);
    private static final String MESSAGE = "El código del problema no es válido";

    /** Código corto canónico, p. ej. {@code 1850A}; es el {@code sourceCode} del catálogo. */
    public String sourceCode() {
        return contestId + index;
    }

    /** URL pública del problema, p. ej. {@code https://codeforces.com/problemset/problem/1850/A}. */
    public String url() {
        return "https://codeforces.com/problemset/problem/" + contestId + "/" + index;
    }

    /** @throws FieldValidationException con el error en la clave {@code code} si el texto no es un problema válido. */
    public static CodeforcesProblemCode parse(String text) {
        String value = text == null ? "" : text.strip();
        if (value.isEmpty()) {
            throw invalid("Indique el código del problema (por ejemplo 1850A) o su URL de Codeforces");
        }
        Matcher matcher = SHORT.matcher(value);
        if (matcher.matches()) {
            return of(matcher.group(1), matcher.group(2));
        }
        matcher = URL.matcher(value);
        if (matcher.matches()) {
            return matcher.group(1) != null ? of(matcher.group(1), matcher.group(2))
                    : of(matcher.group(3), matcher.group(4));
        }
        throw invalid("Use el formato 1850A o la URL de un problema de Codeforces");
    }

    private static CodeforcesProblemCode of(String contestId, String index) {
        int contest = Integer.parseInt(contestId);
        if (contest < 1 || contest > MAX_CONTEST_ID) {
            throw invalid("El concurso debe estar entre 1 y " + MAX_CONTEST_ID
                    + "; los concursos de gimnasio no están disponibles");
        }
        return new CodeforcesProblemCode(contest, index.toUpperCase(Locale.ROOT));
    }

    private static FieldValidationException invalid(String detail) {
        return new FieldValidationException(MESSAGE, Map.of("code", detail));
    }
}

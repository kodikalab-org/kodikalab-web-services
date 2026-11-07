package com.kodika.kodikalab.problems.codeforces;

import com.kodika.kodikalab.common.exception.FieldValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeforcesProblemCodeTests {
    @ParameterizedTest
    @CsvSource({
            "1850A,1850,A",
            "  1850A  ,1850,A",
            "1850a,1850,A",
            "1850B2,1850,B2",
            "1850b2,1850,B2",
            "4A,4,A",
            "CF-1850A,1850,A",
            "cf1850c,1850,C",
            "https://codeforces.com/problemset/problem/1850/A,1850,A",
            "https://codeforces.com/problemset/problem/1850/a,1850,A",
            "http://www.codeforces.com/problemset/problem/1850/B1/,1850,B1",
            "https://codeforces.com/problemset/problem/1850/A?locale=es,1850,A",
            "https://codeforces.com/contest/1850/problem/c,1850,C",
            "HTTPS://CODEFORCES.COM/contest/1850/problem/C#statement,1850,C"
    })
    void parsesCodesAndUrlsNormalizingTheIndexToUppercase(String text, int contestId, String index) {
        var code = CodeforcesProblemCode.parse(text);

        assertThat(code.contestId()).isEqualTo(contestId);
        assertThat(code.index()).isEqualTo(index);
        assertThat(code.sourceCode()).isEqualTo(contestId + index);
        assertThat(code.url()).isEqualTo("https://codeforces.com/problemset/problem/" + contestId + "/" + index);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", "1850", "A", "1850AB", "1850A1B", "18 50A", "0A", "100001A", "-5A",
            "https://example.com/problemset/problem/1850/A", "https://codeforces.com/problemset/problem/1850",
            "https://codeforces.com/problemset/problem/1850/AA", "https://codeforces.com/gym/102000/problem/A",
            "ftp://codeforces.com/problemset/problem/1850/A", "javascript:alert(1)",
            "https://codeforces.com.evil.io/problemset/problem/1850/A"})
    void rejectsAnythingElseWithAFieldErrorOnCode(String text) {
        assertThatThrownBy(() -> CodeforcesProblemCode.parse(text))
                .isInstanceOfSatisfying(FieldValidationException.class,
                        exception -> assertThat(exception.getErrors()).containsOnlyKeys("code"));
    }

    @Test
    void theLargestPublicContestIsAccepted() {
        assertThat(CodeforcesProblemCode.parse("99999Z").contestId()).isEqualTo(99_999);
    }
}

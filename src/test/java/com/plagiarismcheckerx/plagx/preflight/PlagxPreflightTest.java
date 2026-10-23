package com.plagiarismcheckerx.plagx.preflight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlagxPreflightTest {
    private static List<String> codes(DraftResult r) {
        return r.getIssues().stream().map(DraftIssue::getCode).collect(Collectors.toList());
    }

    private static PlagxPreflight.Options opts() {
        return PlagxPreflight.options();
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultShape() {
        Map<String, Object> r = PlagxPreflight.checkDraft("The the cat sat.").toMap();
        assertEquals(Arrays.asList("stats", "errors", "warnings", "infos", "issues"), new ArrayList<>(r.keySet()));
        assertEquals(Arrays.asList("words", "sentences", "paragraphs", "characters", "readingTimeMinutes", "fleschReadingEase"),
            new ArrayList<>(((Map<String, Object>) r.get("stats")).keySet()));
        Map<String, Object> issue = (Map<String, Object>) ((List<Object>) r.get("issues")).get(0);
        assertEquals(Arrays.asList("level", "code", "message", "start", "end", "line", "column", "text", "suggestion"),
            new ArrayList<>(issue.keySet()));
        assertEquals("Suggestion: \"The the\" looks like a repeated word.", issue.get("message"));
    }

    @Test
    void utf16Offsets() {
        DraftIssue i = PlagxPreflight.checkDraft("\uD83D\uDE00 the the end.").getIssues().stream()
            .filter(x -> x.getCode().equals("repeated-word")).findFirst().get();
        assertEquals(Arrays.asList(3, 10, 4), Arrays.asList(i.getStart(), i.getEnd(), i.getColumn()));
        assertEquals(3, PlagxPreflight.checkDraft("a\uD83D\uDE00b").getStats().getCharacters());
    }

    @Test
    void matchesJavaScriptOnNonBmpLettersAndSpecialCasing() {
        // Expected values produced by the JavaScript, Python, PHP, .NET and Dart versions.
        String text = "\uD835\uDC00lpha is fine. \u00DFig news: an \uD835\uDC01eta test. The the \uD835\uDCB3yz word. "
            + "\u039F\u0394\u039F\u03A3 and \u03A3\u039F\u03A6\u0399\u0391. a apple, i think.";
        List<String> got = PlagxPreflight.checkDraft(text).getIssues().stream()
            .map(i -> i.getCode() + "@" + i.getStart() + "-" + i.getEnd() + "=" + i.getSuggestion()).collect(Collectors.toList());
        assertEquals(Arrays.asList("sentence-capital@16-19=SSig", "repeated-word@41-48=The", "a-an@76-77=an",
            "sentence-capital@76-77=A", "lowercase-i@85-86=I"), got);
    }

    @Test
    void helpers() {
        assertEquals(6, PlagxPreflight.countWords("It's a well-known fact, 42 times."));
        assertEquals(Arrays.asList("One.", "Two!"),
            PlagxPreflight.splitSentences("One. Two!").stream().map(Span::getText).collect(Collectors.toList()));
        List<Citation> c = PlagxPreflight.findCitations("\u00E9 (Moore, 2011; Price, 2016, 2018)");
        assertEquals(Arrays.asList("moore:2011", "price:2016", "price:2018"),
            c.stream().map(x -> x.getKey() + ":" + x.getYear()).collect(Collectors.toList()));
        assertEquals(3, c.get(0).getStart());
        assertEquals("greenblatt", PlagxPreflight.findCitations("(Greenblatt 45)", "mla").get(0).getKey());
    }

    @Test
    void options() {
        assertEquals(Arrays.asList("missing-heading", "missing-heading", "word-count-low"), codes(PlagxPreflight.checkDraft(
            "One two. Three four.", opts().minWords(10).requiredHeadings("Introduction", "Conclusion").checks("length"))));
        assertEquals(Collections.emptyList(), codes(PlagxPreflight.checkDraft("the the ,,", opts().checks(new ArrayList<>()))));
        assertTrue(codes(PlagxPreflight.checkDraft("The data were collected.", opts().checks("passive-voice"))).contains("passive-voice"));
        assertThrows(IllegalArgumentException.class, () -> PlagxPreflight.checkDraft("x", opts().style("chicago")));
        assertThrows(IllegalArgumentException.class, () -> PlagxPreflight.checkDraft("x", opts().checks("magic")));
        assertThrows(IllegalArgumentException.class, () -> PlagxPreflight.checkDraft("x", opts().minWords(-1)));
    }

    private static String[] run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Cli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8),
            new ByteArrayInputStream(new byte[0]));
        return new String[] {String.valueOf(code), out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8)};
    }

    @Test
    @SuppressWarnings("unchecked")
    void commandLine(@TempDir Path dir) throws IOException {
        String bad = dir.resolve("bad.txt").toString();
        String clean = dir.resolve("clean.md").toString();
        Files.write(dir.resolve("bad.txt"), "Shown (Smith, 2020). the the end.\n".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("clean.md"), "A clean draft. It has two sentences.\n".getBytes(StandardCharsets.UTF_8));

        String[] r = run("check", bad);
        assertEquals("1", r[0]);
        assertTrue(r[1].contains("missing-reference-section"));
        assertEquals("0", run("check", clean)[0]);
        assertEquals("1", run("check", clean, "--min-words", "50", "--strict")[0]);
        List<Object> json = (List<Object>) Json.parse(run("check", clean, "--json", "--require-heading=Conclusion")[1]);
        List<Object> issues = (List<Object>) ((Map<String, Object>) json.get(0)).get("issues");
        assertEquals(Collections.singletonList("missing-heading"),
            issues.stream().map(i -> ((Map<String, Object>) i).get("code")).collect(Collectors.toList()));

        r = run("stats", clean);
        assertEquals("0", r[0]);
        assertTrue(r[1].contains("Words:               7"));

        String[][] usage = {
            {"check"}, {"check", dir.resolve("paper.docx").toString()}, {"check", dir.resolve("missing.txt").toString()},
            {"check", clean, "--style", "x"}, {"check", clean, "--min-words", "ten"}, {"lint", clean}, {},
        };
        for (String[] args : usage) assertEquals("2", run(args)[0], Arrays.toString(args));
        assertEquals(PlagxPreflight.VERSION + "\n", run("--version")[1]);
    }
}

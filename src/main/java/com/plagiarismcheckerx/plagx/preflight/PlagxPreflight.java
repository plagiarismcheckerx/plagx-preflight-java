package com.plagiarismcheckerx.plagx.preflight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Plagx Preflight: a free draft checker with basic grammar suggestions, by Plagiarism Checker X.
 * Checks word count and structure, in-text citations against the reference list, punctuation
 * and basic grammar, and gives readability stats. Runs fully offline. Results match the
 * JavaScript version exactly.
 */
public final class PlagxPreflight {
    private PlagxPreflight() {}

    /** Library version. */
    public static final String VERSION = "0.1.2";

    /** Check groups. "passive-voice" is off by default. */
    public static final List<String> ALL_CHECKS =
        Collections.unmodifiableList(Arrays.asList("length", "references", "punctuation", "grammar", "passive-voice"));

    /** Checks run when no list is given. */
    public static final List<String> DEFAULT_CHECKS =
        Collections.unmodifiableList(Arrays.asList("length", "references", "punctuation", "grammar"));

    /** Citation styles. */
    public static final List<String> STYLES = Collections.unmodifiableList(Citations.STYLES);

    /** Options for {@link #checkDraft(String, Options)}. Setters return this, for chaining. */
    public static final class Options {
        Integer minWords;
        Integer maxWords;
        String style = "apa";
        List<String> checks = DEFAULT_CHECKS;
        List<String> requiredHeadings = Collections.emptyList();

        /** @param n minimum word count (reference list excluded), or null @return this */
        public Options minWords(Integer n) {
            minWords = n;
            return this;
        }

        /** @param n maximum word count (reference list excluded), or null @return this */
        public Options maxWords(Integer n) {
            maxWords = n;
            return this;
        }

        /** @param s "apa" (default) or "mla" @return this */
        public Options style(String s) {
            style = s;
            return this;
        }

        /** @param list check groups from {@link #ALL_CHECKS} @return this */
        public Options checks(List<String> list) {
            checks = list == null ? DEFAULT_CHECKS : list;
            return this;
        }

        /** @param names check groups from {@link #ALL_CHECKS} @return this */
        public Options checks(String... names) {
            return checks(Arrays.asList(names));
        }

        /** @param list headings that must be present, e.g. "Introduction" @return this */
        public Options requiredHeadings(List<String> list) {
            requiredHeadings = list == null ? Collections.emptyList() : list;
            return this;
        }

        /** @param names headings that must be present @return this */
        public Options requiredHeadings(String... names) {
            return requiredHeadings(Arrays.asList(names));
        }
    }

    /** @return new default options */
    public static Options options() {
        return new Options();
    }

    /**
     * Checks a draft with the default options.
     *
     * @param text the draft
     * @return stats plus a sorted list of suggestions
     */
    public static DraftResult checkDraft(String text) {
        return checkDraft(text, new Options());
    }

    /**
     * Checks a draft and returns stats plus a sorted list of suggestions.
     *
     * @param text the draft
     * @param options options
     * @return the result
     * @throws IllegalArgumentException for an unknown style or check, or a negative word limit
     */
    public static DraftResult checkDraft(String text, Options options) {
        Options o = options == null ? new Options() : options;
        if (!STYLES.contains(o.style)) throw new IllegalArgumentException("Unknown style \"" + o.style + "\". Use: apa, mla");
        for (String c : o.checks) {
            if (!ALL_CHECKS.contains(c)) throw new IllegalArgumentException("Unknown check \"" + c + "\". Use: " + String.join(", ", ALL_CHECKS));
        }
        if (o.minWords != null && o.minWords < 0) throw new IllegalArgumentException("minWords must be a whole number");
        if (o.maxWords != null && o.maxWords < 0) throw new IllegalArgumentException("maxWords must be a whole number");
        Options n = new Options();
        n.minWords = o.minWords;
        n.maxWords = o.maxWords;
        n.style = o.style;
        n.checks = new ArrayList<>();
        for (String c : ALL_CHECKS) {
            if (o.checks.contains(c)) n.checks.add(c);
        }
        n.requiredHeadings = new ArrayList<>(o.requiredHeadings);

        Text.Document doc = Text.structure(text);
        DraftStats stats = stats(text, doc);
        Checks.Context ctx = new Checks.Context(text, n, doc, stats.getWords());
        for (String c : n.checks) {
            switch (c) {
                case "length": Checks.length(ctx); break;
                case "references": Checks.references(ctx); break;
                case "punctuation": Checks.punctuation(ctx); break;
                case "grammar": Checks.grammar(ctx); break;
                default: Checks.passiveVoice(ctx); break;
            }
        }

        int[] starts = Text.lineStarts(text);
        List<DraftIssue> issues = new ArrayList<>();
        for (Checks.RawIssue x : ctx.issues) {
            int line = Text.lineIndex(starts, x.start);
            issues.add(new DraftIssue(x.level, x.code, x.message, x.start, x.end, line + 1, x.start - starts[line] + 1,
                text.substring(x.start, x.end), x.suggestion));
        }
        issues.sort(Comparator.comparingInt(DraftIssue::getStart).thenComparing(DraftIssue::getCode).thenComparingInt(DraftIssue::getEnd));
        int errors = 0;
        int warnings = 0;
        int infos = 0;
        for (DraftIssue i : issues) {
            if (i.getLevel().equals("error")) errors++;
            else if (i.getLevel().equals("warning")) warnings++;
            else infos++;
        }
        return new DraftResult(stats, errors, warnings, infos, issues);
    }

    /** JavaScript Math.round: halves round up, towards +infinity. */
    private static double jsRound(double v) {
        double r = Math.floor(v);
        return v - r >= 0.5 ? r + 1 : r;
    }

    private static DraftStats stats(String t, Text.Document doc) {
        int bodyWords = Text.countWords(t.substring(0, doc.bodyEnd));
        int sentences = 0;
        int sentenceWords = 0;
        int syl = 0;
        int paras = 0;
        for (Text.Paragraph p : doc.body) {
            if (p.heading) continue;
            paras++;
            sentences += Text.paragraphSentences(p).size();
            for (Span w : Text.words(p.getText())) {
                sentenceWords++;
                syl += Text.syllables(w.getText());
            }
        }
        double flesch = 0;
        if (sentences > 0 && sentenceWords > 0) {
            flesch = jsRound((206.835 - 1.015 * ((double) sentenceWords / sentences) - 84.6 * ((double) syl / sentenceWords)) * 10) / 10;
            if (flesch == 0) flesch = 0; // no negative zero
        }
        return new DraftStats(bodyWords, sentences, paras, t.codePointCount(0, t.length()), (bodyWords + 199) / 200, flesch);
    }

    /**
     * @param text any text
     * @return number of words
     */
    public static int countWords(String text) {
        return Text.countWords(text);
    }

    /**
     * @param text any text
     * @return sentences with UTF-16 offsets; headings count as one sentence each
     */
    public static List<Span> splitSentences(String text) {
        return Text.splitSentences(text);
    }

    /**
     * @param text any text
     * @param style "apa" or "mla"
     * @return in-text citations with UTF-16 offsets, sorted by position
     */
    public static List<Citation> findCitations(String text, String style) {
        return Citations.find(text, style);
    }

    /**
     * @param text any text
     * @return APA in-text citations
     */
    public static List<Citation> findCitations(String text) {
        return Citations.find(text, "apa");
    }
}

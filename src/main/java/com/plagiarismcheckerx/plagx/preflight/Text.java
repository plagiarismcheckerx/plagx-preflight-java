package com.plagiarismcheckerx.plagx.preflight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text structure helpers: words, lines, paragraphs, headings, sentences, syllables.
 * All offsets are UTF-16 code-unit offsets into the original text, as in JavaScript.
 */
final class Text {
    private Text() {}

    /** A paragraph: a run of non-blank lines. */
    static final class Paragraph extends Span {
        final boolean heading;

        Paragraph(String text, int start, int end) {
            super(text, start, end);
            heading = isHeading(text);
        }
    }

    /** A document split into body and reference list. */
    static final class Document {
        final List<Paragraph> paragraphs;
        final List<Paragraph> body;
        final int bodyEnd;
        final Paragraph referenceHeading;
        final List<Span> entries;

        Document(List<Paragraph> paragraphs, List<Paragraph> body, int bodyEnd, Paragraph referenceHeading, List<Span> entries) {
            this.paragraphs = paragraphs;
            this.body = body;
            this.bodyEnd = bodyEnd;
            this.referenceHeading = referenceHeading;
            this.entries = entries;
        }
    }

    private static final Pattern WORD = Js.re("[\\p{L}\\p{M}0-9]+(?:['\u2019-][\\p{L}\\p{M}0-9]+)*");

    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList(
        "al", "approx", "cf", "co", "dept", "dr", "ed", "eds", "eg", "et", "etc", "fig", "figs", "ie",
        "inc", "jr", "ltd", "mr", "mrs", "ms", "no", "nos", "p", "pp", "prof", "sr", "st", "vol", "vols", "vs"));

    static final Set<String> REFERENCE_HEADINGS = new HashSet<>(Arrays.asList(
        "references", "reference list", "bibliography", "works cited", "work cited", "sources", "literature cited"));

    static List<Span> words(String text) {
        List<Span> out = new ArrayList<>();
        Matcher m = WORD.matcher(text);
        while (m.find()) out.add(new Span(m.group(), m.start(), m.end()));
        return out;
    }

    static int countWords(String text) {
        Matcher m = WORD.matcher(text);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') starts.add(i + 1);
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    static int lineIndex(int[] starts, int offset) {
        int lo = 0;
        int hi = starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >> 1;
            if (starts[mid] <= offset) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f' || c == 0x0B || c == 0xA0;
    }

    /** Trimmed [start, end) range. */
    private static int[] trimRange(String text, int s, int e) {
        while (s < e && isSpace(text.charAt(s))) s++;
        while (e > s && isSpace(text.charAt(e - 1))) e--;
        return new int[] {s, e};
    }

    private static final Pattern HASHES = Js.re("^#+\\s*");
    private static final Pattern NUMBERING = Js.reI("^(?:\\d+(?:\\.\\d+)*\\.?|[ivxlc]+\\.)\\s+");
    private static final Pattern COLON = Js.re("\\s*:$");
    private static final Pattern SPACES = Js.re("\\s+");
    private static final Pattern HEADING_END = Js.re("[.!?,;\"'\u201D\u2019)\\]]$");
    private static final Pattern HEADING_START = Js.re("^[\\p{Lu}0-9]");
    private static final Pattern BLANK_LINE = Js.re("^[ \\t\\r\\f\\v\\u00a0]*$");

    static String normalizeHeading(String s) {
        String t = Js.replaceFirst(Js.trim(s), HASHES, "");
        t = Js.replaceFirst(t, NUMBERING, "");
        t = Js.replaceFirst(t, COLON, "");
        return Js.lower(Js.replaceAll(t, SPACES, " "));
    }

    private static boolean isHeading(String t) {
        if (t.contains("\n")) return false;
        if (t.startsWith("#")) return true;
        if (REFERENCE_HEADINGS.contains(normalizeHeading(t))) return true;
        int n = countWords(t);
        if (n < 1 || n > 10) return false;
        if (Js.test(HEADING_END, t)) return false;
        return Js.test(HEADING_START, t);
    }

    static List<Paragraph> paragraphs(String text) {
        List<Paragraph> out = new ArrayList<>();
        int[] starts = lineStarts(text);
        int blockStart = -1;
        int blockEnd = -1;
        for (int i = 0; i < starts.length; i++) {
            int ls = starts[i];
            int le = i + 1 < starts.length ? starts[i + 1] - 1 : text.length();
            if (Js.test(BLANK_LINE, text.substring(ls, le))) {
                if (blockStart >= 0) out.add(paragraph(text, blockStart, blockEnd));
                blockStart = -1;
            } else {
                if (blockStart < 0) blockStart = ls;
                blockEnd = le;
            }
        }
        if (blockStart >= 0) out.add(paragraph(text, blockStart, blockEnd));
        return out;
    }

    private static Paragraph paragraph(String text, int start, int end) {
        int[] r = trimRange(text, start, end);
        return new Paragraph(text.substring(r[0], r[1]), r[0], r[1]);
    }

    static Document structure(String text) {
        List<Paragraph> paras = paragraphs(text);
        int idx = -1;
        for (int i = 0; i < paras.size(); i++) {
            Paragraph p = paras.get(i);
            if (p.heading && REFERENCE_HEADINGS.contains(normalizeHeading(p.getText()))) {
                idx = i;
                break;
            }
        }
        List<Paragraph> body = idx < 0 ? paras : paras.subList(0, idx);
        Paragraph heading = idx < 0 ? null : paras.get(idx);
        List<Span> entries = new ArrayList<>();
        if (heading != null) {
            int[] starts = lineStarts(text);
            for (int i = 0; i < starts.length; i++) {
                int ls = starts[i];
                if (ls <= heading.getEnd()) continue;
                int le = i + 1 < starts.length ? starts[i + 1] - 1 : text.length();
                int[] r = trimRange(text, ls, le);
                if (r[1] > r[0]) entries.add(new Span(text.substring(r[0], r[1]), r[0], r[1]));
            }
        }
        return new Document(paras, body, heading == null ? text.length() : heading.getStart(), heading, entries);
    }

    private static final Pattern TERMINATOR = Js.re("[.!?]+[\"'\u201D\u2019)\\]]*(?=\\s|$)");
    private static final Pattern CLOSERS = Js.re("[\"'\u201D\u2019)\\]]+$");
    private static final Pattern BEFORE = Js.re("(?:^|[\\s(])([\\p{L}.]+)$");
    private static final Pattern ONE_LETTER = Js.re("^\\p{L}$");

    private static List<Span> sentencesIn(String s, int base) {
        List<Span> out = new ArrayList<>();
        int start = 0;
        Matcher m = TERMINATOR.matcher(s);
        while (m.find()) {
            String marks = Js.replaceFirst(m.group(), CLOSERS, "");
            if (marks.startsWith("..")) continue; // an ellipsis
            if (marks.equals(".")) {
                // A lone period after an abbreviation, an initial or a dotted form (U.S.) does not end a sentence.
                Js.Match b = Js.first(BEFORE, s.substring(0, m.start()));
                if (b != null) {
                    String w = Js.lower(b.group(1));
                    if (ABBREVIATIONS.contains(w) || Js.test(ONE_LETTER, w) || w.contains(".")) continue;
                }
            }
            start = push(out, s, base, start, m.end());
        }
        push(out, s, base, start, s.length());
        return out;
    }

    private static int push(List<Span> out, String s, int base, int start, int end) {
        int[] r = trimRange(s, start, end);
        if (r[1] > r[0]) out.add(new Span(s.substring(r[0], r[1]), base + r[0], base + r[1]));
        return end;
    }

    static List<Span> splitSentences(String text) {
        List<Span> out = new ArrayList<>();
        for (Paragraph p : paragraphs(text)) {
            if (p.heading) out.add(new Span(p.getText(), p.getStart(), p.getEnd()));
            else out.addAll(sentencesIn(p.getText(), p.getStart()));
        }
        return out;
    }

    static List<Span> paragraphSentences(Paragraph p) {
        return sentencesIn(p.getText(), p.getStart());
    }

    private static final Pattern NOT_AZ = Js.re("[^a-z]");
    private static final Pattern ENDING = Js.re("(?:[^laeiouy]es|ed|[^laeiouy]e)$");
    private static final Pattern VOWELS = Js.re("[aeiouy]+");

    static int syllables(String word) {
        String w = Js.replaceAll(Js.lower(word), NOT_AZ, "");
        if (w.isEmpty()) return 0;
        if (w.length() <= 3) return 1;
        w = Js.replaceFirst(w, ENDING, "");
        if (w.startsWith("y")) w = w.substring(1);
        int n = Js.all(VOWELS, w).size();
        return n > 0 ? n : 1;
    }
}

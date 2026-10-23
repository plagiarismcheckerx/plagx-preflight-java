package com.plagiarismcheckerx.plagx.preflight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** In-text citations (APA author-date, MLA author-page) and reference list entries. */
final class Citations {
    private Citations() {}

    static final List<String> STYLES = Arrays.asList("apa", "mla");

    /** A reference list entry with its key and year. */
    static final class Entry extends Span {
        final String key;
        final String year;

        Entry(String text, int start, int end, String key, String year) {
            super(text, start, end);
            this.key = key;
            this.year = year;
        }
    }

    private static final String YEAR = "(?:\\d{4}[a-z]?|n\\.d\\.)";
    private static final String NAME = "\\p{Lu}[\\p{L}'\\u2019-]+";
    private static final Set<String> NOT_AUTHORS = new HashSet<>(Arrays.asList(
        "appendix", "chapter", "chapters", "fig", "figure", "figures", "line", "lines", "p", "page", "pages", "part",
        "phase", "pp", "section", "see", "step", "table", "tables", "version", "vol", "volume"));

    private static final Pattern WORD = Js.re("\\p{L}[\\p{L}'\\u2019-]*");
    private static final Pattern LEAD = Js.re("^\\s+");
    private static final Pattern APA_PAREN = Js.re("\\(([^()]*?" + YEAR + "[^()]*)\\)");
    private static final Pattern APA_PREFIX = Js.reI("^(?:e\\.g\\.|see also|see|cf\\.),?\\s+");
    private static final Pattern APA_AUTHOR = Js.re("^(.*?\\p{L}.*?),?\\s+(" + YEAR + "(?:,\\s*" + YEAR + ")*)(?=$|,)");
    private static final Pattern YEAR_SPLIT = Js.re(",\\s*");
    private static final Pattern UPPER_START = Js.re("^\\p{Lu}");
    private static final Pattern NARRATIVE = Js.re("((?:" + NAME + "\\s+){0,4}" + NAME + "(?:\\s+(?:and|&)\\s+" + NAME
        + "|\\s+et\\.?\\s*al\\.*)?)\\s+\\((" + YEAR + ")(?:,[^()]*)?\\)");
    private static final Pattern MLA_PAREN = Js.re("\\(([^()]+)\\)");
    private static final Pattern MLA_PART = Js.re("^(\\p{Lu}[\\p{L}'\\u2019.\\- &,]*?),?\\s+(\\d+(?:\\s*[-\\u2013]\\s*\\d+)?)$");
    private static final Pattern FOUR_DIGITS = Js.re("^\\d{4}$");
    private static final Pattern ENTRY_MARKER = Js.re("^(?:\\[\\d+\\]|\\d+\\.|[-*\\u2022])\\s+");
    private static final Pattern ENTRY_YEAR = Js.re("\\((" + YEAR + ")");

    private static String lowerKey(String s) {
        return Js.lower(s).replace('\u2019', '\'');
    }

    private static String firstWord(String s) {
        Js.Match m = Js.first(WORD, s);
        return m == null ? "" : lowerKey(m.group(0));
    }

    /** The parts of a citation group split on ";", each trimmed, with its start offset. */
    private static List<Object[]> parts(String inner, int offset) {
        List<Object[]> out = new ArrayList<>();
        for (String part : inner.split(";", -1)) {
            int lead = part.length() - Js.replaceFirst(part, LEAD, "").length();
            out.add(new Object[] {Js.trim(part), offset + lead});
            offset += part.length() + 1;
        }
        return out;
    }

    private static List<Citation> apa(String text) {
        List<Citation> out = new ArrayList<>();
        // Parenthetical: (Smith, 2020), (Smith & Lee, 2020, p. 4; Brown, 2018a)
        Matcher m = APA_PAREN.matcher(text);
        while (m.find()) {
            for (Object[] part : parts(m.group(1), m.start() + 1)) {
                String trimmed = (String) part[0];
                int pStart = (Integer) part[1];
                Js.Match px = Js.first(APA_PREFIX, trimmed);
                int skip = px == null ? 0 : px.end;
                String rest = trimmed.substring(skip);
                Js.Match am = Js.first(APA_AUTHOR, rest);
                if (am == null) continue;
                String author = am.group(1);
                if (!Js.test(UPPER_START, author) || NOT_AUTHORS.contains(firstWord(author))) continue;
                for (String year : YEAR_SPLIT.split(am.group(2), -1)) {
                    out.add(new Citation(rest, pStart + skip, pStart + trimmed.length(), author, firstWord(author), Js.lower(year)));
                }
            }
        }
        // Narrative: Smith (2020), Smith and Lee (2020, p. 4), Smith et al. (2020)
        m = NARRATIVE.matcher(text);
        while (m.find()) {
            out.add(new Citation(m.group(), m.start(), m.end(), m.group(1), firstWord(m.group(1)), Js.lower(m.group(2))));
        }
        return out;
    }

    private static List<Citation> mla(String text) {
        List<Citation> out = new ArrayList<>();
        // (Smith 50), (Smith and Lee 50-52), (Smith et al. 12; Brown 7)
        Matcher m = MLA_PAREN.matcher(text);
        while (m.find()) {
            for (Object[] part : parts(m.group(1), m.start() + 1)) {
                String trimmed = (String) part[0];
                int pStart = (Integer) part[1];
                Js.Match pm = Js.first(MLA_PART, trimmed);
                if (pm == null || NOT_AUTHORS.contains(firstWord(pm.group(1))) || Js.test(FOUR_DIGITS, pm.group(2))) continue;
                out.add(new Citation(trimmed, pStart, pStart + trimmed.length(), pm.group(1), firstWord(pm.group(1)), null));
            }
        }
        return out;
    }

    static List<Citation> find(String text, String style) {
        if (!STYLES.contains(style)) throw new IllegalArgumentException("Unknown style \"" + style + "\". Use: apa, mla");
        List<Citation> list = style.equals("apa") ? apa(text) : mla(text);
        // List.sort is stable, as in JavaScript.
        list.sort(Comparator.comparingInt(Citation::getStart).thenComparingInt(Citation::getEnd));
        return list;
    }

    /** Whether a citation points at a reference entry. */
    static boolean matches(Citation c, Entry e) {
        if (c.getYear() != null && !c.getYear().equals(e.year)) return false;
        for (Js.Match w : Js.all(WORD, c.getAuthor())) {
            if (lowerKey(w.group(0)).equals(e.key)) return true;
        }
        return false;
    }

    static Entry parseEntry(Span part) {
        String body = Js.replaceFirst(part.getText(), ENTRY_MARKER, "");
        Js.Match y = Js.first(ENTRY_YEAR, body);
        return new Entry(part.getText(), part.getStart(), part.getEnd(), firstWord(body), y == null ? null : Js.lower(y.group(1)));
    }
}

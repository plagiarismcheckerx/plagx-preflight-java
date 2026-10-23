package com.plagiarismcheckerx.plagx.preflight;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The individual check groups. Each reports issues through {@link Context#add}. */
final class Checks {
    private Checks() {}

    static final int LONG_SENTENCE_WORDS = 40;
    static final int LONG_PARAGRAPH_WORDS = 250;

    /** An issue before line and column are added. */
    static final class RawIssue {
        final String level;
        final String code;
        final String message;
        final int start;
        final int end;
        final String suggestion;

        RawIssue(String level, String code, String message, int start, int end, String suggestion) {
            this.level = level;
            this.code = code;
            this.message = message;
            this.start = start;
            this.end = end;
            this.suggestion = suggestion;
        }
    }

    // Codes inside URLs, email addresses and `code` are skipped.
    private static final Pattern MASKED = Js.re("^(?:unmatched-|double-space|space-before|missing-space|repeated-|mixed-quote|a-an"
        + "|sentence-capital|lowercase-i|common-confusion|passive-voice)");
    private static final Pattern MASK = Js.re("\\bhttps?:\\/\\/[^\\s<>()]+|\\bwww\\.[^\\s<>()]+"
        + "|[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+|`[^`\\n]*`");

    /** State shared by the checks for one checkDraft() call. */
    static final class Context {
        final String text;
        final PlagxPreflight.Options opts;
        final Text.Document doc;
        final int words;
        final String bodyText;
        private final List<int[]> masks = new ArrayList<>();
        private final Set<String> seen = new HashSet<>();
        final List<RawIssue> issues = new ArrayList<>();

        Context(String text, PlagxPreflight.Options opts, Text.Document doc, int words) {
            this.text = text;
            this.opts = opts;
            this.doc = doc;
            this.words = words;
            bodyText = text.substring(0, doc.bodyEnd);
            Matcher m = MASK.matcher(text);
            while (m.find()) masks.add(new int[] {m.start(), m.end()});
        }

        List<Text.Paragraph> body() {
            return doc.body;
        }

        void add(String level, String code, int start, int end, String message, String suggestion) {
            if (Js.test(MASKED, code)) {
                for (int[] m : masks) {
                    if (start < m[1] && end > m[0]) return;
                }
            }
            if (!seen.add(code + ":" + start + ":" + end + ":" + message)) return;
            issues.add(new RawIssue(level, code, message, start, end, suggestion));
        }

        void add(String level, String code, int start, int end, String message) {
            add(level, code, start, end, message, null);
        }

        void addDoc(String level, String code, String message) {
            add(level, code, 0, 0, message, null);
        }
    }

    private static final Pattern UPPER_START = Js.re("^\\p{Lu}");

    private static String matchCase(String fix, String original) {
        return Js.test(UPPER_START, original) ? Js.upperFirst(fix) : fix;
    }

    private static boolean startsWithAny(String w, List<String> prefixes) {
        for (String p : prefixes) {
            if (w.startsWith(p)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- length

    private static final Pattern LIST_ITEM = Js.re("^(?:[-*+\u2022]|\\d+[.)])\\s");

    static void length(Context ctx) {
        int w = ctx.words;
        PlagxPreflight.Options o = ctx.opts;
        if (o.minWords != null && w < o.minWords) {
            ctx.addDoc("warning", "word-count-low", "Suggestion: the draft has " + w + " words; the minimum is " + o.minWords + ".");
        }
        if (o.maxWords != null && w > o.maxWords) {
            ctx.addDoc("warning", "word-count-high", "Suggestion: the draft has " + w + " words; the maximum is " + o.maxWords + ".");
        }
        for (Text.Paragraph p : ctx.body()) {
            if (p.heading) continue;
            List<Span> sentences = Text.paragraphSentences(p);
            for (Span s : sentences) {
                int n = Text.countWords(s.getText());
                if (n > LONG_SENTENCE_WORDS) {
                    ctx.add("warning", "long-sentence", s.getStart(), s.getEnd(),
                        "Suggestion: this sentence has " + n + " words; consider splitting it (over " + LONG_SENTENCE_WORDS + ").");
                }
            }
            int n = Text.countWords(p.getText());
            if (n > LONG_PARAGRAPH_WORDS) {
                ctx.add("info", "long-paragraph", p.getStart(), p.getEnd(),
                    "Suggestion: this paragraph has " + n + " words; consider splitting it (over " + LONG_PARAGRAPH_WORDS + ").");
            }
            if (sentences.size() == 1 && !Js.test(LIST_ITEM, p.getText())) {
                ctx.add("info", "single-sentence-paragraph", p.getStart(), p.getEnd(), "Suggestion: this paragraph has only one sentence.");
            }
        }
        Set<String> headings = new HashSet<>();
        for (Text.Paragraph p : ctx.doc.paragraphs) {
            if (p.heading) headings.add(Text.normalizeHeading(p.getText()));
        }
        for (String h : o.requiredHeadings) {
            if (!headings.contains(Text.normalizeHeading(h))) {
                ctx.addDoc("warning", "missing-heading", "Suggestion: add a \"" + h + "\" heading.");
            }
        }
    }

    // ---------------------------------------------------------------- references

    private static final Pattern ET_AL = Js.reI("\\bet\\.?\\s*al\\b\\.*");

    static void references(Context ctx) {
        Text.Document doc = ctx.doc;
        List<Citation> cites = Citations.find(ctx.bodyText, ctx.opts.style);
        if (!cites.isEmpty() && doc.referenceHeading == null) {
            ctx.addDoc("error", "missing-reference-section",
                "Suggestion: the draft has citations but no reference list heading (References or Works Cited).");
        }
        List<Citations.Entry> entries = new ArrayList<>();
        for (Span e : doc.entries) entries.add(Citations.parseEntry(e));
        if (doc.referenceHeading != null) {
            for (Citation c : cites) {
                if (entries.stream().noneMatch(e -> Citations.matches(c, e))) {
                    ctx.add("error", "citation-no-reference", c.getStart(), c.getEnd(),
                        "Suggestion: \"" + c.getText() + "\" has no matching entry in the reference list.");
                }
            }
            for (Citations.Entry e : entries) {
                if (cites.stream().noneMatch(c -> Citations.matches(c, e))) {
                    ctx.add("warning", "reference-not-cited", e.getStart(), e.getEnd(), "Suggestion: this reference is never cited in the text.");
                }
            }
        }
        for (Js.Match m : Js.all(ET_AL, ctx.bodyText)) {
            if (!Js.lower(m.group(0)).equals("et al.")) {
                ctx.add("warning", "et-al-format", m.start, m.end, "Suggestion: write \"et al.\" with one period.", "et al.");
            }
        }
    }

    // ---------------------------------------------------------------- punctuation

    private static final Pattern LIST_MARKER_BEFORE = Js.re("(?:^|\\s)[A-Za-z0-9]{1,3}$");
    private static final Pattern DOUBLE_SPACE = Js.re("(?<=\\S) {2,}(?=\\S)");
    private static final Pattern SPACE_BEFORE = Js.re("(?<=[\\p{L}0-9)\\]\"\u201d]) +([,;:!?.])(?=\\s|$)");
    private static final Pattern MISSING_SPACE = Js.re("[,;:!?](?=[A-Za-z])|(?<=[a-z]{2})\\.(?=[A-Z][a-z])");
    private static final Pattern REPEATED = Js.re("([!?,;:])\\1+|(?<!\\.)\\.\\.(?!\\.)");
    private static final String UNMATCHED = "Suggestion: this quotation mark has no matching pair.";

    private static char opener(char c) {
        return c == ')' ? '(' : c == ']' ? '[' : c == '}' ? '{' : 0;
    }

    static void punctuation(Context ctx) {
        int straight = -1;
        int curly = -1;
        for (Text.Paragraph p : ctx.body()) {
            String t = p.getText();
            int at = p.getStart();

            // Quotation marks
            List<Integer> quotes = new ArrayList<>();
            for (int i = 0; i < t.length(); i++) {
                if (t.charAt(i) == '"') quotes.add(i);
            }
            if (!quotes.isEmpty() && straight < 0) straight = at + quotes.get(0);
            if (quotes.size() % 2 == 1) {
                int i = quotes.get(quotes.size() - 1);
                ctx.add("warning", "unmatched-quote", at + i, at + i + 1, UNMATCHED);
            }
            List<Integer> open = new ArrayList<>();
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if ((c == '\u201c' || c == '\u201d') && curly < 0) curly = at + i;
                if (c == '\u201c') {
                    open.add(i);
                } else if (c == '\u201d') {
                    if (!open.isEmpty()) open.remove(open.size() - 1);
                    else ctx.add("warning", "unmatched-quote", at + i, at + i + 1, UNMATCHED);
                }
            }
            for (int i : open) ctx.add("warning", "unmatched-quote", at + i, at + i + 1, UNMATCHED);

            // Brackets
            Deque<Integer> stack = new ArrayDeque<>();
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == '(' || c == '[' || c == '{') {
                    stack.push(i);
                } else if (opener(c) != 0) {
                    if (!stack.isEmpty() && t.charAt(stack.peek()) == opener(c)) {
                        stack.pop();
                    } else {
                        // List markers such as "a)", "ii)" or "1)".
                        if (c == ')' && Js.test(LIST_MARKER_BEFORE, t.substring(0, i))) continue;
                        ctx.add("warning", "unmatched-bracket", at + i, at + i + 1, "Suggestion: \"" + c + "\" has no matching bracket.");
                    }
                }
            }
            List<Integer> left = new ArrayList<>(stack);
            for (int k = left.size() - 1; k >= 0; k--) {
                int i = left.get(k);
                ctx.add("warning", "unmatched-bracket", at + i, at + i + 1, "Suggestion: \"" + t.charAt(i) + "\" has no matching bracket.");
            }

            for (Js.Match m : Js.all(DOUBLE_SPACE, t)) {
                ctx.add("info", "double-space", at + m.start, at + m.end, "Suggestion: use a single space.", " ");
            }
            for (Js.Match m : Js.all(SPACE_BEFORE, t)) {
                ctx.add("warning", "space-before-punctuation", at + m.start, at + m.end,
                    "Suggestion: remove the space before \"" + m.group(1) + "\".", m.group(1));
            }
            for (Js.Match m : Js.all(MISSING_SPACE, t)) {
                ctx.add("warning", "missing-space-after-punctuation", at + m.start, at + m.start + 1,
                    "Suggestion: add a space after \"" + m.group(0) + "\".", m.group(0) + " ");
            }
            for (Js.Match m : Js.all(REPEATED, t)) {
                ctx.add("warning", "repeated-punctuation", at + m.start, at + m.end,
                    "Suggestion: \"" + m.group(0) + "\" repeats punctuation.", m.group(0).substring(0, 1));
            }
        }
        if (straight >= 0 && curly >= 0) {
            int i = Math.max(straight, curly);
            ctx.add("info", "mixed-quote-styles", i, i + 1, "Suggestion: the draft mixes straight and curly quotation marks.");
        }
    }

    // ---------------------------------------------------------------- grammar

    private static final List<String> SILENT_H = Arrays.asList("hour", "honest", "honor", "honour", "heir");
    private static final List<String> CONSONANT_SOUND = Arrays.asList(
        "uni", "use", "usa", "usu", "uti", "ure", "uro", "urani", "eu", "ewe", "one", "once", "ubiq", "unanim");
    private static final List<String> UNCERTAIN = Arrays.asList("herb", "histor", "hotel", "unin", "unim", "unid", "oner");

    private static final class Confusion {
        final Pattern re;
        final Function<Js.Match, String> fix;

        Confusion(String re, Function<Js.Match, String> fix) {
            this.re = Js.reI(re);
            this.fix = fix;
        }
    }

    private static final List<Confusion> CONFUSIONS = Arrays.asList(
        new Confusion("\\b(could|would|should|must|might) of\\b", m -> m.group(1) + " have"),
        new Confusion("\\bits (a|an|been|not|going)\\b", m -> "it's " + m.group(1)),
        new Confusion("\\bit['\u2019]s own\\b", m -> "its own"),
        new Confusion("\\b(more|less|better|worse|rather|greater|fewer|larger|smaller|higher|lower|faster|slower|easier|harder) then\\b",
            m -> m.group(1) + " than"),
        new Confusion("\\btheir (is|are|was|were)\\b", m -> "there " + m.group(1)),
        new Confusion("\\bthere own\\b", m -> "their own"),
        new Confusion("\\b(the|an) affect\\b", m -> m.group(1) + " effect"),
        new Confusion("\\b(may|might|will|can|could|would|should|negatively|positively|greatly|significantly) effect\\b",
            m -> m.group(1) + " affect"),
        new Confusion("\\bloose (weight|track|control)\\b", m -> "lose " + m.group(1)),
        new Confusion("\\balot\\b", m -> "a lot"));

    private static final Pattern PASSIVE = Js.reI("\\b(?:am|is|are|was|were|be|been|being)\\s+(?:[a-z]+ly\\s+)?(?:[a-z]{3,}ed|written|done"
        + "|made|given|taken|shown|seen|known|found|held|chosen|built|drawn|taught|thought|brought|said|told|sent|kept|paid|begun"
        + "|broken|driven|eaten|forgotten|hidden|spoken|stolen|worn)\\b");
    private static final Pattern I_FORM = Js.re("^i(['\u2019](?:m|ve|ll|d))?$");
    private static final Pattern GAP = Js.re("^\\s+$");
    private static final Pattern HAS_LETTER = Js.re("\\p{L}");
    private static final Pattern LOWER_START = Js.re("^\\p{Ll}");
    private static final Pattern HAS_UPPER = Js.re("\\p{Lu}");
    private static final Pattern DIGIT = Js.re("[0-9]");
    private static final Pattern ASCII_LOWER_START = Js.re("^[a-z]");
    private static final Pattern VOWEL_START = Js.re("^[aeiou]");

    static void grammar(Context ctx) {
        for (Text.Paragraph p : ctx.body()) {
            String t = p.getText();
            int ps = p.getStart();
            List<Span> toks = Text.words(t);
            for (int i = 0; i < toks.size(); i++) {
                Span tok = toks.get(i);
                String low = Js.lower(tok.getText());
                Span next = i + 1 < toks.size() && Js.test(GAP, t.substring(tok.getEnd(), toks.get(i + 1).getStart())) ? toks.get(i + 1) : null;

                if (next != null && Js.lower(next.getText()).equals(low) && Js.test(HAS_LETTER, low) && !low.equals("had") && !low.equals("that")) {
                    ctx.add("warning", "repeated-word", ps + tok.getStart(), ps + next.getEnd(),
                        "Suggestion: \"" + tok.getText() + " " + next.getText() + "\" looks like a repeated word.", tok.getText());
                }

                if (next != null && (low.equals("a") || low.equals("an"))) {
                    String nw = next.getText().split("-", -1)[0];
                    String nl = Js.lower(nw);
                    boolean acronym = nw.length() > 1 && nw.equals(Js.upper(nw));
                    if (nw.length() > 1 && !Js.test(DIGIT, nw) && !acronym && Js.test(ASCII_LOWER_START, nl) && !startsWithAny(nl, UNCERTAIN)) {
                        boolean vowel = startsWithAny(nl, SILENT_H) || (Js.test(VOWEL_START, nl) && !startsWithAny(nl, CONSONANT_SOUND));
                        String expected = vowel ? "an" : "a";
                        if (!low.equals(expected)) {
                            ctx.add("warning", "a-an", ps + tok.getStart(), ps + tok.getEnd(),
                                "Suggestion: use \"" + expected + "\" before \"" + next.getText() + "\".", matchCase(expected, tok.getText()));
                        }
                    }
                }

                Js.Match im = Js.first(I_FORM, tok.getText());
                if (im != null) {
                    char before = tok.getStart() > 0 ? t.charAt(tok.getStart() - 1) : 0;
                    char after = tok.getEnd() < t.length() ? t.charAt(tok.getEnd()) : 0;
                    if (im.group(1) != null || (after != '.' && after != ')' && before != '.')) {
                        String fix = Js.upperFirst(tok.getText());
                        ctx.add("warning", "lowercase-i", ps + tok.getStart(), ps + tok.getEnd(),
                            "Suggestion: use \"" + fix + "\" instead of \"" + tok.getText() + "\".", fix);
                    }
                }
            }

            if (!p.heading) {
                for (Span s : Text.paragraphSentences(p)) {
                    List<Span> ws = Text.words(s.getText());
                    if (ws.isEmpty()) continue;
                    Span first = ws.get(0);
                    String st = s.getText();
                    if (first.getStart() == 0
                        && Js.test(LOWER_START, first.getText())
                        && !Js.test(HAS_UPPER, first.getText())
                        && !Js.test(I_FORM, first.getText())
                        && (first.getEnd() >= st.length() || st.charAt(first.getEnd()) != ')')) {
                        ctx.add("warning", "sentence-capital", s.getStart(), s.getStart() + first.getEnd(),
                            "Suggestion: start the sentence with a capital letter.", Js.upperFirst(first.getText()));
                    }
                }
            }

            for (Confusion cf : CONFUSIONS) {
                for (Js.Match m : Js.all(cf.re, t)) {
                    String suggestion = matchCase(cf.fix.apply(m), m.group(0));
                    ctx.add("warning", "common-confusion", ps + m.start, ps + m.end,
                        "Suggestion: \"" + m.group(0) + "\" may be \"" + suggestion + "\".", suggestion);
                }
            }
        }
    }

    static void passiveVoice(Context ctx) {
        for (Text.Paragraph p : ctx.body()) {
            for (Js.Match m : Js.all(PASSIVE, p.getText())) {
                ctx.add("info", "passive-voice", p.getStart() + m.start, p.getStart() + m.end,
                    "Suggestion: \"" + m.group(0) + "\" may be passive voice.");
            }
        }
    }
}

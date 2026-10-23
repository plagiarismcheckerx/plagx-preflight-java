package com.plagiarismcheckerx.plagx.preflight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JavaScript string and regex semantics the port needs. Patterns are written in JavaScript
 * syntax and translated, so \s, \b, ., $ and \p{..} behave exactly as in the reference.
 */
final class Js {
    private Js() {}

    /** JavaScript's \s set. */
    private static final String SPACE = "\\t\\n\\x0B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF";
    private static final String WORD = "A-Za-z0-9_";
    private static final String BOUNDARY = "(?:(?<=[" + WORD + "])(?![" + WORD + "])|(?<![" + WORD + "])(?=[" + WORD + "]))";

    /** Compiles a JavaScript regex. */
    static Pattern re(String js) {
        return Pattern.compile(translate(js));
    }

    /** Compiles a JavaScript regex with the i flag (ASCII case folding, as in JavaScript without u). */
    static Pattern reI(String js) {
        return Pattern.compile(translate(js), Pattern.CASE_INSENSITIVE);
    }

    private static String property(String name) {
        switch (name) {
            case "L": return UnicodeData.L;
            case "M": return UnicodeData.M;
            case "Lu": return UnicodeData.LU;
            case "Ll": return UnicodeData.LL;
            default: throw new IllegalArgumentException("Unsupported property " + name);
        }
    }

    /** Translates JavaScript regex syntax to java.util.regex with the same meaning. */
    static String translate(String js) {
        StringBuilder out = new StringBuilder();
        boolean inClass = false;
        for (int i = 0; i < js.length(); i++) {
            char c = js.charAt(i);
            if (c == '\\') {
                char n = js.charAt(++i);
                if (n == 'p' && js.charAt(i + 1) == '{') {
                    int close = js.indexOf('}', i);
                    String body = property(js.substring(i + 2, close));
                    out.append(inClass ? body : "[" + body + "]");
                    i = close;
                } else if (n == 's') {
                    out.append(inClass ? SPACE : "[" + SPACE + "]");
                } else if (n == 'S' && !inClass) {
                    out.append("[^").append(SPACE).append(']');
                } else if (n == 'b' && !inClass) {
                    out.append(BOUNDARY);
                } else if (n == 'v') {
                    out.append("\\x0B"); // Java's \v is a class of vertical whitespace
                } else if (n == '/') {
                    out.append('/');
                } else {
                    out.append(c).append(n);
                }
            } else if (inClass) {
                if (c == ']') inClass = false;
                // In Java, [ and && inside a class mean union and intersection.
                if (c == '[' || c == '&') out.append('\\');
                out.append(c);
            } else if (c == '[') {
                inClass = true;
                out.append(c);
                if (js.charAt(i + 1) == '^') out.append(js.charAt(++i));
            } else if (c == '.') {
                out.append("[^\\n\\r\\u2028\\u2029]");
            } else if (c == '$') {
                out.append("\\z");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** A regex match with UTF-16 offsets. */
    static final class Match {
        final int start;
        final int end;
        private final String[] groups;

        Match(Matcher m) {
            start = m.start();
            end = m.end();
            groups = new String[m.groupCount() + 1];
            for (int g = 0; g <= m.groupCount(); g++) groups[g] = m.group(g);
        }

        String group(int g) {
            return groups[g];
        }
    }

    /** All matches, like String.prototype.matchAll. */
    static List<Match> all(Pattern p, String s) {
        List<Match> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        while (m.find()) out.add(new Match(m));
        return out;
    }

    static Match first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? new Match(m) : null;
    }

    static boolean test(Pattern p, String s) {
        return p.matcher(s).find();
    }

    static String replaceFirst(String s, Pattern p, String with) {
        return p.matcher(s).replaceFirst(Matcher.quoteReplacement(with));
    }

    static String replaceAll(String s, Pattern p, String with) {
        return p.matcher(s).replaceAll(Matcher.quoteReplacement(with));
    }

    private static final Pattern TRIM = re("^\\s+|\\s+$");

    /** String.prototype.trim(). */
    static String trim(String s) {
        return replaceAll(s, TRIM, "");
    }

    private static final Map<Integer, int[]> UPPER = load(UnicodeData.UPPER);
    private static final Map<Integer, int[]> LOWER = load(UnicodeData.LOWER);

    private static Map<Integer, int[]> load(String data) {
        Map<Integer, int[]> map = new HashMap<>();
        for (String entry : data.split(",")) {
            int eq = entry.indexOf('=');
            String[] to = entry.substring(eq + 1).split(" ");
            int[] cps = new int[to.length];
            for (int i = 0; i < to.length; i++) cps[i] = Integer.parseInt(to[i], 16);
            map.put(Integer.parseInt(entry.substring(0, eq), 16), cps);
        }
        return map;
    }

    private static boolean cased(int cp) {
        return UPPER.containsKey(cp) || LOWER.containsKey(cp);
    }

    /** String.prototype.toLowerCase(), including the final-sigma rule. */
    static String lower(String s) {
        int[] cps = s.codePoints().toArray();
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < cps.length; i++) {
            int cp = cps[i];
            if (cp == 0x3A3) {
                // Final sigma: after a cased letter and not before one.
                boolean before = i > 0 && cased(cps[i - 1]);
                boolean after = i + 1 < cps.length && cased(cps[i + 1]);
                out.appendCodePoint(before && !after ? 0x3C2 : 0x3C3);
                continue;
            }
            int[] m = LOWER.get(cp);
            if (m == null) out.appendCodePoint(cp);
            else for (int x : m) out.appendCodePoint(x);
        }
        return out.toString();
    }

    /** String.prototype.toUpperCase(). */
    static String upper(String s) {
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            int[] m = UPPER.get(cp);
            if (m == null) out.appendCodePoint(cp);
            else for (int x : m) out.appendCodePoint(x);
        });
        return out.toString();
    }

    /** s.charAt(0).toUpperCase() + s.slice(1): only the first UTF-16 unit changes. */
    static String upperFirst(String s) {
        if (s.isEmpty() || Character.isSurrogate(s.charAt(0))) return s;
        return upper(s.substring(0, 1)) + s.substring(1);
    }
}

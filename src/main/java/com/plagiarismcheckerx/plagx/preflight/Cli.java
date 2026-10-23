package com.plagiarismcheckerx.plagx.preflight;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Plagx Preflight command line. A free academic writing utility by Plagiarism Checker X. */
public final class Cli {
    private Cli() {}

    private static final String HELP = "Plagx Preflight " + PlagxPreflight.VERSION + ": check a draft before you submit\n"
        + "\n"
        + "Usage:\n"
        + "  plagx-preflight check <file> [...]   List suggestions for length, references,\n"
        + "                                       punctuation and basic grammar\n"
        + "  plagx-preflight stats <file> [...]   Print word count, sentences and readability\n"
        + "\n"
        + "<file> is a .txt or .md file. Use - to read from stdin.\n"
        + "\n"
        + "Options:\n"
        + "  --min-words <n>               Minimum word count\n"
        + "  --max-words <n>               Maximum word count\n"
        + "  --style apa|mla               Citation style (default: apa)\n"
        + "  --checks <list>               Comma-separated: " + String.join(",", PlagxPreflight.ALL_CHECKS) + "\n"
        + "                                (default: all except passive-voice)\n"
        + "  --require-heading <name>      Required heading (repeatable)\n"
        + "  --strict                      check: treat warnings as errors\n"
        + "  --json                        Print the result as JSON\n"
        + "  -v, --version                 Show version\n"
        + "  -h, --help                    Show this help\n"
        + "\n"
        + "Exit codes: 0 = no errors, 1 = errors found, 2 = usage or input error.\n"
        + "\n"
        + "Automated suggestions only. A free academic writing utility by Plagiarism\n"
        + "Checker X, which checks originality and AI-generated text: https://plagiarismcheckerx.com/";

    private static final class UsageError extends Exception {
        private static final long serialVersionUID = 1L;

        UsageError(String message) {
            super(message);
        }
    }

    private static final class Parsed {
        final PlagxPreflight.Options opts = new PlagxPreflight.Options();
        final List<String> headings = new ArrayList<>();
        final List<String> args = new ArrayList<>();
        boolean strict;
        boolean json;
        boolean help;
        boolean version;
    }

    private static List<String> csv(String s) {
        List<String> out = new ArrayList<>();
        for (String x : s.split(",", -1)) {
            if (!x.trim().isEmpty()) out.add(x.trim());
        }
        return out;
    }

    private static Parsed parse(String[] argv) throws UsageError {
        Parsed p = new Parsed();
        for (int i = 0; i < argv.length; i++) {
            String a = argv[i];
            String inline = null;
            if (a.startsWith("--") && a.contains("=")) {
                inline = a.substring(a.indexOf('=') + 1);
                a = a.substring(0, a.indexOf('='));
            }
            boolean takesValue = a.equals("--min-words") || a.equals("--max-words") || a.equals("--style")
                || a.equals("--checks") || a.equals("--require-heading");
            String value = null;
            if (takesValue) {
                if (inline != null) {
                    value = inline;
                } else {
                    if (i + 1 >= argv.length) throw new UsageError(a + " needs a value");
                    value = argv[++i];
                }
            }
            switch (a) {
                case "--min-words": p.opts.minWords(number(a, value)); break;
                case "--max-words": p.opts.maxWords(number(a, value)); break;
                case "--style": p.opts.style(value); break;
                case "--checks": p.opts.checks(csv(value)); break;
                case "--require-heading": p.headings.add(value); break;
                case "--strict": p.strict = true; break;
                case "--json": p.json = true; break;
                case "-h": case "--help": p.help = true; break;
                case "-v": case "--version": p.version = true; break;
                default:
                    if (a.startsWith("-") && !a.equals("-")) throw new UsageError("Unknown option " + a);
                    p.args.add(a);
            }
        }
        p.opts.requiredHeadings(p.headings);
        if (!PlagxPreflight.STYLES.contains(p.opts.style)) {
            throw new UsageError("Unknown style \"" + p.opts.style + "\". Use: " + String.join(", ", PlagxPreflight.STYLES));
        }
        for (String c : p.opts.checks) {
            if (!PlagxPreflight.ALL_CHECKS.contains(c)) {
                throw new UsageError("Unknown check \"" + c + "\". Use: " + String.join(", ", PlagxPreflight.ALL_CHECKS));
            }
        }
        return p;
    }

    private static int number(String option, String v) throws UsageError {
        if (!v.matches("[0-9]+")) throw new UsageError(option + " needs a whole number");
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new UsageError(option + " needs a whole number");
        }
    }

    private static String load(String path, InputStream stdin) throws UsageError {
        String lower = path.toLowerCase(Locale.ROOT);
        if (!path.equals("-") && !lower.endsWith(".txt") && !lower.endsWith(".md")) {
            throw new UsageError(path + ": only .txt and .md files are supported");
        }
        try {
            byte[] bytes;
            if (path.equals("-")) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = stdin.read(chunk)) > 0) buf.write(chunk, 0, n);
                bytes = buf.toByteArray();
            } else {
                bytes = Files.readAllBytes(Paths.get(path));
            }
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (NoSuchFileException e) {
            throw new UsageError("Cannot read " + path + ": file not found");
        } catch (CharacterCodingException e) {
            throw new UsageError("Cannot read " + path + ": not valid UTF-8");
        } catch (IOException e) {
            throw new UsageError("Cannot read " + path + ": " + e.getMessage());
        }
    }

    private static String printStats(String file, DraftStats s) {
        return file + "\n"
            + "  Words:               " + s.getWords() + "\n"
            + "  Sentences:           " + s.getSentences() + "\n"
            + "  Paragraphs:          " + s.getParagraphs() + "\n"
            + "  Characters:          " + s.getCharacters() + "\n"
            + "  Reading time:        " + s.getReadingTimeMinutes() + " min\n"
            + "  Flesch reading ease: " + s.toMap().get("fleschReadingEase").toString().replaceAll("\\.0$", "");
    }

    private static String pad(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    private static String printCheck(String file, DraftResult r) {
        StringBuilder sb = new StringBuilder();
        for (DraftIssue x : r.getIssues()) {
            sb.append(file).append(':').append(x.getLine()).append(':').append(x.getColumn()).append("  ")
                .append(pad(x.getLevel(), 7)).append(' ').append(pad(x.getCode(), 32)).append(' ').append(x.getMessage()).append('\n');
        }
        sb.append(file).append(": ").append(r.getStats().getWords()).append(" words, ").append(r.getErrors()).append(" error(s), ")
            .append(r.getWarnings()).append(" warning(s), ").append(r.getInfos()).append(" info");
        return sb.toString();
    }

    /**
     * Runs the command line.
     *
     * @param argv arguments
     * @param out standard output
     * @param err standard error
     * @param stdin standard input (for "-")
     * @return the exit code
     */
    public static int run(String[] argv, PrintStream out, PrintStream err, InputStream stdin) {
        Parsed p;
        try {
            p = parse(argv);
        } catch (UsageError e) {
            err.print(e.getMessage() + "\n\nRun plagx-preflight --help for usage.\n");
            return 2;
        }
        if (p.version) {
            out.print(PlagxPreflight.VERSION + "\n");
            return 0;
        }
        if (p.help || p.args.isEmpty()) {
            out.print(HELP + "\n");
            return p.help ? 0 : 2;
        }
        String command = p.args.get(0);
        List<String> files = p.args.subList(1, p.args.size());
        List<String> names = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        try {
            if (!command.equals("check") && !command.equals("stats")) throw new UsageError("Unknown command \"" + command + "\"");
            if (files.isEmpty()) throw new UsageError(command + " needs a file (.txt or .md, or - for stdin)");
            for (String f : files) {
                names.add(f);
                bodies.add(load(f, stdin));
            }
        } catch (UsageError e) {
            err.print(e.getMessage() + "\n\nRun plagx-preflight --help for usage.\n");
            return 2;
        }

        boolean failed = false;
        List<DraftResult> results = new ArrayList<>();
        for (String text : bodies) {
            DraftResult r = command.equals("stats")
                ? PlagxPreflight.checkDraft(text, new PlagxPreflight.Options().checks(new ArrayList<>()))
                : PlagxPreflight.checkDraft(text, p.opts);
            if (r.getErrors() > 0 || (p.strict && r.getWarnings() > 0)) failed = true;
            results.add(r);
        }
        StringBuilder sb = new StringBuilder();
        if (p.json) {
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("file", names.get(i));
                if (command.equals("stats")) m.put("stats", results.get(i).getStats().toMap());
                else m.putAll(results.get(i).toMap());
                list.add(m);
            }
            sb.append(Json.stringifyPretty(list));
        } else {
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) sb.append("\n\n");
                sb.append(command.equals("stats") ? printStats(names.get(i), results.get(i).getStats()) : printCheck(names.get(i), results.get(i)));
            }
        }
        out.print(sb.append('\n'));
        return command.equals("stats") || !failed ? 0 : 1;
    }

    /**
     * Entry point: {@code java -jar plagx-preflight.jar check essay.txt}.
     *
     * @param args arguments
     */
    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err, System.in));
    }
}

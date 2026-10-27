# Plagx Preflight for Java

*A free academic writing tool by Plagiarism Checker X.*

**Plagx Preflight for Java is a free, open-source draft checker for Java apps.** Use it as an essay checker before students submit: it checks the word count and structure, matches in-text citations against the reference list, and gives punctuation and basic grammar suggestions plus readability stats.

- No dependencies, works offline: text never leaves the machine
- Java 11+, also usable from Kotlin
- APA and MLA citation checks
- Command line with JSON output and CI-friendly exit codes
- Results identical to the [JavaScript version](https://github.com/plagiarismcheckerx/plagx-preflight), offsets included

**[Try the live demo →](https://plagiarismcheckerx.github.io/plagx-preflight/)**

Developer downloads: [npm](https://www.npmjs.com/package/plagx-preflight) · [PyPI](https://pypi.org/project/plagx-preflight/) · [NuGet](https://www.nuget.org/packages/Plagx.Preflight) · [Packagist](https://packagist.org/packages/plagiarismcheckerx/plagx-preflight) · [GitHub](https://github.com/plagiarismcheckerx/plagx-preflight)

> Plagx Preflight is a free academic writing utility by Plagiarism Checker X. It checks length, structure, references, punctuation and basic grammar, and every result is a suggestion. Plagiarism checking and AI-generated text detection are available in Plagiarism Checker X, linked at the end of this page.

## Install

Maven:

```xml
<dependency>
  <groupId>io.github.plagiarismcheckerx</groupId>
  <artifactId>plagx-preflight</artifactId>
  <version>0.1.2</version>
</dependency>
```

Gradle:

```kotlin
implementation("io.github.plagiarismcheckerx:plagx-preflight:0.1.2")
```

## Quick start

```java
import com.plagiarismcheckerx.plagx.preflight.DraftIssue;
import com.plagiarismcheckerx.plagx.preflight.DraftResult;
import com.plagiarismcheckerx.plagx.preflight.PlagxPreflight;

DraftResult result = PlagxPreflight.checkDraft(text, PlagxPreflight.options()
    .minWords(1500).maxWords(2000).style("apa").requiredHeadings("Introduction", "Conclusion"));

System.out.println(result.getStats().getWords() + " words, Flesch " + result.getStats().getFleschReadingEase());
for (DraftIssue issue : result.getIssues()) {
    System.out.println(issue.getLine() + ":" + issue.getColumn() + " " + issue.getLevel() + " " + issue.getMessage());
}
// 5:14 warning Suggestion: "the the" looks like a repeated word.
```

## Command line

```bash
java -jar plagx-preflight-0.1.2.jar check essay.txt --min-words 1500 --max-words 2000 --style apa
java -jar plagx-preflight-0.1.2.jar check essay.md --json
java -jar plagx-preflight-0.1.2.jar stats essay.txt
```

| Option | Meaning |
|---|---|
| `--min-words <n>`, `--max-words <n>` | Word limits (reference list excluded) |
| `--style apa\|mla` | Citation style (default `apa`) |
| `--checks <list>` | Comma-separated check groups |
| `--require-heading <name>` | Required heading, repeatable |
| `--strict` | Count warnings as failures |
| `--json` | JSON output |

Exit codes: `0` no errors, `1` errors found (with `--strict`, warnings too), `2` usage or input error. Accepts `.txt` and `.md` (UTF-8).

## API

`PlagxPreflight.checkDraft(text)` or `checkDraft(text, options)` returns a `DraftResult` with `getStats()`, `getErrors()`, `getWarnings()`, `getInfos()` and `getIssues()`. Each `DraftIssue` has `getLevel()`, `getCode()`, `getMessage()`, `getStart()`, `getEnd()`, `getLine()`, `getColumn()`, `getText()` and `getSuggestion()`. Issues are sorted by position, and `toJson()` gives the same JSON as the other Plagx Preflight libraries.

`start`, `end` and `column` are UTF-16 offsets, so they index Java strings directly (for example with `text.substring(issue.getStart(), issue.getEnd())`).

| Option | Default | |
|---|---|---|
| `minWords(n)`, `maxWords(n)` | `null` | Word limits (reference list excluded) |
| `style(s)` | `"apa"` | `"apa"` or `"mla"` |
| `checks(...)` | all except `"passive-voice"` | `length`, `references`, `punctuation`, `grammar`, `passive-voice` |
| `requiredHeadings(...)` | none | e.g. `"Introduction", "Conclusion"` |

Helpers: `countWords(text)`, `splitSentences(text)`, `findCitations(text, style)`, `VERSION`, `ALL_CHECKS`. Invalid options throw `IllegalArgumentException`.

## Checks

| Group | Codes | Level |
|---|---|---|
| Length & structure | `word-count-low`, `word-count-high`, `long-sentence` (over 40 words), `long-paragraph` (over 250 words), `single-sentence-paragraph`, `missing-heading` | warning / info |
| References | `citation-no-reference`, `missing-reference-section`, `reference-not-cited`, `et-al-format` | error / warning |
| Punctuation | `unmatched-quote`, `unmatched-bracket`, `double-space`, `space-before-punctuation`, `missing-space-after-punctuation`, `repeated-punctuation`, `mixed-quote-styles` | warning / info |
| Grammar | `repeated-word`, `a-an`, `sentence-capital`, `lowercase-i`, `common-confusion`, `passive-voice` (off by default) | warning / info |

## Good to know

- Every result is a suggestion. The grammar rules are deliberately conservative, so they miss some mistakes rather than flag correct text.
- The reference list starts at a heading such as References, Bibliography or Works Cited. It is excluded from the word count and from the punctuation and grammar checks.

## Development

```bash
mvn test
```

`src/test/resources/parity/expected.json` and `src/test/resources/fixtures/` come from the JavaScript reference (`npm run parity` in plagx-preflight). Every case must match exactly. `UnicodeData.java` holds the JavaScript Unicode letter classes and case mappings (`node scripts/build-unicode.mjs`), because Java's own tables are older. Sources are plain ASCII.

## Plagx Preflight - Free Draft & Grammar Checker

An open-source writing utility for students, researchers and developers. Check length, structure, citations, punctuation and basic grammar before you submit.

Developed by Plagiarism Checker X.

Plagx Preflight gives writing suggestions; it does not detect plagiarism or AI-generated text. For full document checks, explore [Plagiarism Checker X](https://plagiarismcheckerx.com/?utm_source=plagx-preflight-java) and its [AI Content Detector](https://plagiarismcheckerx.com/ai-content-detector?utm_source=plagx-preflight-java).

## License

MIT © Plagiarism Checker X

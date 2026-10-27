# Changelog

## 0.1.3: 2026-10-27

- Documentation updates. No changes to the API or output.

## 0.1.2: 2026-10-23

First Java release, matching Plagx Preflight 0.1.2 for JavaScript.

- `checkDraft()`: word count and structure, in-text citations against the reference list (APA, MLA), punctuation and basic grammar suggestions, readability stats
- Helpers: `countWords()`, `splitSentences()`, `findCitations()`
- Command line: `java -jar plagx-preflight-0.1.2.jar check essay.txt`
- No dependencies, works offline, Java 11+
- Results match the JavaScript version on all 160 parity cases

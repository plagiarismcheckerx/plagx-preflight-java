package com.plagiarismcheckerx.plagx.preflight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Result of {@link PlagxPreflight#checkDraft}. */
public final class DraftResult {
    private final DraftStats stats;
    private final int errors;
    private final int warnings;
    private final int infos;
    private final List<DraftIssue> issues;

    DraftResult(DraftStats stats, int errors, int warnings, int infos, List<DraftIssue> issues) {
        this.stats = stats;
        this.errors = errors;
        this.warnings = warnings;
        this.infos = infos;
        this.issues = Collections.unmodifiableList(issues);
    }

    /** @return word count and readability */
    public DraftStats getStats() {
        return stats;
    }

    /** @return number of error-level issues */
    public int getErrors() {
        return errors;
    }

    /** @return number of warning-level issues */
    public int getWarnings() {
        return warnings;
    }

    /** @return number of info-level issues */
    public int getInfos() {
        return infos;
    }

    /** @return issues sorted by position */
    public List<DraftIssue> getIssues() {
        return issues;
    }

    /** @return the result as an ordered map (the JavaScript object shape) */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stats", stats.toMap());
        m.put("errors", errors);
        m.put("warnings", warnings);
        m.put("infos", infos);
        List<Object> list = new ArrayList<>();
        for (DraftIssue i : issues) list.add(i.toMap());
        m.put("issues", list);
        return m;
    }

    /** @return the result as JSON, the same as the other Plagx Preflight libraries */
    public String toJson() {
        return Json.stringify(toMap());
    }
}

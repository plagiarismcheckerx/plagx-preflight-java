package com.plagiarismcheckerx.plagx.preflight;

import java.util.LinkedHashMap;
import java.util.Map;

/** An in-text citation with UTF-16 offsets. */
public final class Citation extends Span {
    private final String author;
    private final String key;
    private final String year;

    Citation(String text, int start, int end, String author, String key, String year) {
        super(text, start, end);
        this.author = author;
        this.key = key;
        this.year = year;
    }

    /** @return the author text, e.g. "Smith and Lee" */
    public String getAuthor() {
        return author;
    }

    /** @return the first word of the author text in lowercase */
    public String getKey() {
        return key;
    }

    /** @return the year (APA), or null for MLA */
    public String getYear() {
        return year;
    }

    /** @return the citation as an ordered map (the JavaScript object shape) */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", getText());
        m.put("start", getStart());
        m.put("end", getEnd());
        m.put("author", author);
        m.put("key", key);
        m.put("year", year);
        return m;
    }
}

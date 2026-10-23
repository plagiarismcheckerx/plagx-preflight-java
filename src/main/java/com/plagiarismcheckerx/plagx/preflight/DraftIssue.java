package com.plagiarismcheckerx.plagx.preflight;

import java.util.LinkedHashMap;
import java.util.Map;

/** One suggestion. {@code start}, {@code end} and {@code column} are UTF-16 offsets, so they index Java strings directly. */
public final class DraftIssue {
    private final String level;
    private final String code;
    private final String message;
    private final int start;
    private final int end;
    private final int line;
    private final int column;
    private final String text;
    private final String suggestion;

    DraftIssue(String level, String code, String message, int start, int end, int line, int column, String text, String suggestion) {
        this.level = level;
        this.code = code;
        this.message = message;
        this.start = start;
        this.end = end;
        this.line = line;
        this.column = column;
        this.text = text;
        this.suggestion = suggestion;
    }

    /** @return "error", "warning" or "info" */
    public String getLevel() {
        return level;
    }

    /** @return the check code, e.g. "repeated-word" */
    public String getCode() {
        return code;
    }

    /** @return the message */
    public String getMessage() {
        return message;
    }

    /** @return start offset (UTF-16) */
    public int getStart() {
        return start;
    }

    /** @return end offset (UTF-16, exclusive) */
    public int getEnd() {
        return end;
    }

    /** @return line number, from 1 */
    public int getLine() {
        return line;
    }

    /** @return column, from 1 */
    public int getColumn() {
        return column;
    }

    /** @return the flagged text */
    public String getText() {
        return text;
    }

    /** @return a suggested replacement, or null */
    public String getSuggestion() {
        return suggestion;
    }

    /** @return the issue as an ordered map (the JavaScript object shape) */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("code", code);
        m.put("message", message);
        m.put("start", start);
        m.put("end", end);
        m.put("line", line);
        m.put("column", column);
        m.put("text", text);
        m.put("suggestion", suggestion);
        return m;
    }
}

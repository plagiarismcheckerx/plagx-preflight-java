package com.plagiarismcheckerx.plagx.preflight;

/** A piece of text with UTF-16 offsets, so {@code start} and {@code end} index Java strings directly. */
public class Span {
    private final String text;
    private final int start;
    private final int end;

    Span(String text, int start, int end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }

    /** @return the text */
    public String getText() {
        return text;
    }

    /** @return start offset (UTF-16) */
    public int getStart() {
        return start;
    }

    /** @return end offset (UTF-16, exclusive) */
    public int getEnd() {
        return end;
    }

    @Override
    public String toString() {
        return text;
    }
}

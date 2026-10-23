package com.plagiarismcheckerx.plagx.preflight;

import java.util.LinkedHashMap;
import java.util.Map;

/** Word count and readability of a draft. */
public final class DraftStats {
    private final int words;
    private final int sentences;
    private final int paragraphs;
    private final int characters;
    private final int readingTimeMinutes;
    private final double fleschReadingEase;

    DraftStats(int words, int sentences, int paragraphs, int characters, int readingTimeMinutes, double fleschReadingEase) {
        this.words = words;
        this.sentences = sentences;
        this.paragraphs = paragraphs;
        this.characters = characters;
        this.readingTimeMinutes = readingTimeMinutes;
        this.fleschReadingEase = fleschReadingEase;
    }

    /** @return words in the body (the reference list is excluded) */
    public int getWords() {
        return words;
    }

    /** @return sentences in body paragraphs */
    public int getSentences() {
        return sentences;
    }

    /** @return body paragraphs, headings excluded */
    public int getParagraphs() {
        return paragraphs;
    }

    /** @return Unicode code points in the whole text */
    public int getCharacters() {
        return characters;
    }

    /** @return reading time at 200 words per minute, rounded up */
    public int getReadingTimeMinutes() {
        return readingTimeMinutes;
    }

    /** @return Flesch reading ease, one decimal place */
    public double getFleschReadingEase() {
        return fleschReadingEase;
    }

    /** @return the stats as an ordered map (the JavaScript object shape) */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("words", words);
        m.put("sentences", sentences);
        m.put("paragraphs", paragraphs);
        m.put("characters", characters);
        m.put("readingTimeMinutes", readingTimeMinutes);
        m.put("fleschReadingEase", fleschReadingEase);
        return m;
    }
}

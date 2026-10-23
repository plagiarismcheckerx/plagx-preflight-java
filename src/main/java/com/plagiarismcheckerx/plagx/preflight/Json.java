package com.plagiarismcheckerx.plagx.preflight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer, so Plagx Preflight needs no dependencies.
 * Objects become {@code LinkedHashMap<String, Object>} (key order kept), arrays {@code ArrayList<Object>},
 * whole numbers {@code Long} and other numbers {@code Double}.
 */
final class Json {
    private Json() {}

    /**
     * Parses JSON text.
     *
     * @param text JSON text
     * @return a Map, List, String, Long, Double, Boolean or null
     * @throws IllegalArgumentException when the text is not valid JSON
     */
    public static Object parse(String text) {
        Reader r = new Reader(text);
        r.space();
        Object value = r.value();
        r.space();
        if (r.i < text.length()) throw r.error("Unexpected text after JSON value");
        return value;
    }

    /**
     * Writes a value as compact JSON, like JavaScript's {@code JSON.stringify}.
     *
     * @param value a Map, Iterable, String, Number, Boolean or null
     * @return JSON text
     */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, null, "");
        return sb.toString();
    }

    /**
     * Writes a value as indented JSON (two spaces).
     *
     * @param value a Map, Iterable, String, Number, Boolean or null
     * @return JSON text
     */
    public static String stringifyPretty(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, "  ", "");
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value, String indent, String current) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            quote(sb, (String) value);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value instanceof Number ? number((Number) value) : value.toString());
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            String inner = indent == null ? null : current + indent;
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                if (inner != null) sb.append('\n').append(inner);
                quote(sb, String.valueOf(e.getKey()));
                sb.append(inner != null ? ": " : ":");
                write(sb, e.getValue(), indent, inner);
            }
            if (inner != null) sb.append('\n').append(current);
            sb.append('}');
        } else if (value instanceof Iterable) {
            String inner = indent == null ? null : current + indent;
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) sb.append(',');
                first = false;
                if (inner != null) sb.append('\n').append(inner);
                write(sb, item, indent, inner);
            }
            if (!first && inner != null) sb.append('\n').append(current);
            sb.append(']');
        } else if (value instanceof JsonObject) {
            write(sb, ((JsonObject) value).toMap(), indent, current);
        } else {
            quote(sb, value.toString());
        }
    }

    /** Numbers as JavaScript prints them: whole numbers without ".0". */
    private static String number(Number n) {
        if (n instanceof Double || n instanceof Float) {
            double d = n.doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        }
        return n.toString();
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    /** Result objects that can be written as JSON. */
    public interface JsonObject {
        /**
         * The object as a map of JSON values.
         *
         * @return the fields in order
         */
        Map<String, Object> toMap();
    }

    private static final class Reader {
        final String s;
        int i;

        Reader(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + i);
        }

        void space() {
            while (i < s.length() && " \t\n\r".indexOf(s.charAt(i)) >= 0) i++;
        }

        Object value() {
            if (i >= s.length()) throw error("Unexpected end of JSON");
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': return literal("true", Boolean.TRUE);
                case 'f': return literal("false", Boolean.FALSE);
                case 'n': return literal("null", null);
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) return number();
                    throw error("Unexpected character '" + c + "'");
            }
        }

        Object literal(String word, Object value) {
            if (!s.startsWith(word, i)) throw error("Invalid literal");
            i += word.length();
            return value;
        }

        Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            space();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return map;
            }
            while (true) {
                space();
                if (i >= s.length() || s.charAt(i) != '"') throw error("Expected a property name");
                String key = string();
                space();
                if (i >= s.length() || s.charAt(i) != ':') throw error("Expected ':'");
                i++;
                space();
                map.put(key, value());
                space();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    return map;
                }
                throw error("Expected ',' or '}'");
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++;
            space();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (true) {
                space();
                list.add(value());
                space();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                    return list;
                }
                throw error("Expected ',' or ']'");
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            i++;
            while (true) {
                if (i >= s.length()) throw error("Unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (i >= s.length()) throw error("Unterminated string");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw error("Invalid unicode escape");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("Invalid unicode escape");
                        }
                        i += 4;
                        break;
                    default: throw error("Invalid escape");
                }
            }
        }

        Object number() {
            int start = i;
            if (s.charAt(i) == '-') i++;
            while (i < s.length() && "0123456789".indexOf(s.charAt(i)) >= 0) i++;
            boolean whole = true;
            if (i < s.length() && s.charAt(i) == '.') {
                whole = false;
                i++;
                while (i < s.length() && "0123456789".indexOf(s.charAt(i)) >= 0) i++;
            }
            if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                whole = false;
                i++;
                if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
                while (i < s.length() && "0123456789".indexOf(s.charAt(i)) >= 0) i++;
            }
            String n = s.substring(start, i);
            try {
                if (whole && n.length() < 19) return Long.parseLong(n);
                return Double.parseDouble(n);
            } catch (NumberFormatException ex) {
                throw error("Invalid number");
            }
        }
    }
}

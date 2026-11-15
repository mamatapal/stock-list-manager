package app.stocklist.common.util;

import java.util.Collection;
import java.util.Map;

/** Minimal JSON writer for audit-log details (flat maps of strings, numbers, booleans and string lists). */
public final class Json {
    private Json() {}

    public static String of(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(e.getKey())).append(':').append(value(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static String value(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Collection<?> c) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : c) {
                if (!first) sb.append(',');
                first = false;
                sb.append(value(o));
            }
            return sb.append(']').toString();
        }
        return quote(v.toString());
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.append('"').toString();
    }
}

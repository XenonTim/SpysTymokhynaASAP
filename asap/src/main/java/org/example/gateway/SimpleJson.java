package org.example.gateway;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Дуже простий JSON-кодек для плоских об'єктів {"key":"value", ...}.
 *
 * Навмисно не використовує жодної зовнішньої бібліотеки — браузерний клієнт
 * і так працює лише з плоскими полями (login, password, chatId, content...),
 * тож повноцінний JSON-парсер тут надлишковий. Формат сумісний з тим, який
 * вже генерує org.example.shared.protocol.PayloadBuilder на боці сервера.
 */
public class SimpleJson {

    public static Map<String, String> parse(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null) return map;

        String body = json.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
        if (body.isBlank()) return map;

        for (String pair : splitTopLevel(body)) {
            int sep = findKeyValueSeparator(pair);
            if (sep < 0) continue;
            String rawKey = pair.substring(0, sep).trim();
            String rawVal = pair.substring(sep + 1).trim();
            map.put(unquote(rawKey), unquote(rawVal));
        }
        return map;
    }

    public static String toJson(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(e.getKey())).append("\":");
            sb.append("\"").append(escape(e.getValue() == null ? "" : e.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static java.util.List<String> splitTopLevel(String body) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int depth = 0;
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && (i == 0 || body.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (!inQuotes) {
                if (c == '{' || c == '[') depth++;
                if (c == '}' || c == ']') depth--;
            }
            if (c == ',' && depth == 0 && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) parts.add(current.toString());
        return parts;
    }

    private static int findKeyValueSeparator(String pair) {
        boolean inQuotes = false;
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            if (c == '"' && (i == 0 || pair.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            }
            if (c == ':' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }

    private static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        return t.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

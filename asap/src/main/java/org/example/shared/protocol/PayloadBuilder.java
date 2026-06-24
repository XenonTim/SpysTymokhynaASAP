package org.example.shared.protocol;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PayloadBuilder {

    private final Map<String, String> fields = new HashMap<>();

    public PayloadBuilder add(String key, String value) {
        fields.put(key, value);
        return this;
    }

    public byte[] build() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(e.getKey())).append("\":");
            sb.append("\"").append(escape(e.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static Map<String, String> parse(byte[] payload) {
        String json = new String(payload, StandardCharsets.UTF_8).trim();
        Map<String, String> map = new HashMap<>();
        if (payload == null || payload.length == 0) {
            return map;
        }
        json = json.replaceAll("^\\{|\\}$", "");
        for (String pair : json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            String[] kv = pair.split("\":\"", 2);
            if (kv.length == 2) {
                String key = kv[0].replaceAll("\"", "").trim();
                String val = kv[1].replaceAll("\"$", "").trim();
                map.put(key, unescape(val));
            }
        }
        return map;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}

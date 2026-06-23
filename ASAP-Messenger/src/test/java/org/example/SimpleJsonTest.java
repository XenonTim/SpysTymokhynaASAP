package org.example;

import org.example.gateway.SimpleJson;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimpleJsonTest {

    @Test
    void parseFlatObject() {
        Map<String, String> parsed = SimpleJson.parse("{\"type\":\"LOGIN_REQUEST\",\"login\":\"kate\"}");

        assertEquals("LOGIN_REQUEST", parsed.get("type"));
        assertEquals("kate", parsed.get("login"));
    }

    @Test
    void toJsonAndBackRoundtrip() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("type", "SEND_MSG");
        fields.put("content", "say \"hi\"");

        String json = SimpleJson.toJson(fields);
        Map<String, String> parsed = SimpleJson.parse(json);

        assertEquals("SEND_MSG", parsed.get("type"));
        assertEquals("say \"hi\"", parsed.get("content"));
    }

    @Test
    void emptyObjectParsesToEmptyMap() {
        Map<String, String> parsed = SimpleJson.parse("{}");
        assertTrue(parsed.isEmpty());
    }

    @Test
    void commaInsideQuotedValueIsNotASeparator() {
        Map<String, String> parsed = SimpleJson.parse("{\"members\":\"kate,kseniia\",\"type\":\"PRIVATE\"}");
        assertEquals("kate,kseniia", parsed.get("members"));
        assertEquals("PRIVATE", parsed.get("type"));
    }
}

package org.example;

import org.example.shared.protocol.PayloadBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayloadBuilderTest {

    @Test
    void buildAndParseRoundtrip() {
        byte[] payload = new PayloadBuilder()
                .add("login", "kate")
                .add("password", "pass123")
                .build();

        Map<String, String> parsed = PayloadBuilder.parse(payload);

        assertEquals("kate", parsed.get("login"));
        assertEquals("pass123", parsed.get("password"));
    }

    @Test
    void specialCharsAreEscaped() {
        byte[] payload = new PayloadBuilder()
                .add("msg", "say \"hello\"")
                .build();

        Map<String, String> parsed = PayloadBuilder.parse(payload);
        assertEquals("say \"hello\"", parsed.get("msg"));
    }
}

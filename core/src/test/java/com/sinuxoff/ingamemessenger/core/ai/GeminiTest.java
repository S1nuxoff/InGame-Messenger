package com.sinuxoff.ingamemessenger.core.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeminiTest {
    private static String model(String name, boolean generates) {
        return "{\"name\":\"" + name + "\",\"supportedGenerationMethods\":[" + (generates ? "\"generateContent\"" : "\"embedContent\"") + "]}";
    }

    @Test
    void picksTheBestModelsFirst() {
        String list = "{\"models\":[" + String.join(",",
                model("models/gemini-2.5-flash", true),
                model("models/gemini-3.0-flash-preview", true),
                model("models/gemini-3.0-flash-lite", true),
                model("models/gemini-2.5-flash-image", true),
                model("models/gemini-2.5-flash-preview-tts", true),
                model("models/gemini-2.5-pro", true),
                model("models/gemini-3.0-flash", true),
                model("models/text-embedding-004", false),
                model("models/gemini-2.0-flash-exp", true)) + "]}";
        assertEquals(List.of("models/gemini-3.0-flash", "models/gemini-3.0-flash-preview", "models/gemini-2.5-flash",
                "models/gemini-3.0-flash-lite"), Gemini.rankModels(list));
    }

    @Test
    void retryDelay() {
        assertEquals(42_000, Gemini.retryDelay("{\"retryDelay\": \"42s\"}"));
        assertEquals(60_000, Gemini.retryDelay("{}"));
        assertEquals(3_600_000, Gemini.retryDelay("\"quotaId\": \"GenerateRequestsPerDayPerProjectPerModel\", \"retryDelay\": \"30s\""));
    }

    @Test
    void answerText() {
        String json = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"# Hi\\n\\n\\n\\nthere \"},{\"text\":\"!\"}]}}]}";
        assertEquals("**Hi**\n\nthere !", Gemini.textOf(json));
        assertNull(Gemini.textOf("{\"candidates\":[]}"));
        assertNull(Gemini.textOf("not json"));
        String longOne = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + "a".repeat(3000) + "\"}]}}]}";
        assertEquals(Gemini.MAX_ANSWER + 1, Gemini.textOf(longOne).length());
    }

    @Test
    void requestBody() {
        JsonObject voice = Gemini.voiceTurn(new byte[]{1, 2, 3}, "note");
        assertTrue(Gemini.isVoice(voice));
        assertFalse(Gemini.isVoice(Gemini.turn("user", "hi")));
        JsonObject body = JsonParser.parseString(Gemini.request("be kind", List.of(Gemini.turn("user", "hi"), voice))).getAsJsonObject();
        assertEquals("be kind", body.getAsJsonObject("systemInstruction").getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
        assertFalse(body.getAsJsonObject("systemInstruction").has("role"));
        assertEquals(2, body.getAsJsonArray("contents").size());
        assertEquals("AQID", voice.getAsJsonArray("parts").get(0).getAsJsonObject().getAsJsonObject("inline_data").get("data").getAsString());
    }
}

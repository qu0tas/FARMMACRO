package com.farmmacro.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class BugReportTest {
    @Test
    void redactsPersonalDataInLogs() {
        String log = "[Render thread/INFO] Setting user: SteveFarmer\n"
                + "Loading /home/vadim/.minecraft/mods from C:/Users/vadim\n"
                + "Connecting to 192.168.1.20:25565, uuid 069a79f4-44e9-4726-a5be-fca90e38aaf5\n"
                + "mail me: someone@example.com https://discord.com/api/webhooks/123/abcdefghijklmnopqrstuvwxyz\n"
                + "token=abc123 SteveFarmer2 stays";
        String out = BugReport.redact(log, List.of("SteveFarmer"), "/home/vadim");
        assertFalse(out.contains("SteveFarmer\n"), out);
        assertTrue(out.contains("Setting user: [PLAYER]"), out);
        assertTrue(out.contains("SteveFarmer2"), "чужие слова с ником внутри не трогаем");
        assertTrue(out.contains("~/.minecraft"), out);
        assertTrue(out.contains("[IP]") && !out.contains("192.168"), out);
        assertTrue(out.contains("[UUID]"), out);
        assertTrue(out.contains("[EMAIL]"), out);
        assertTrue(out.contains("[URL]") && !out.contains("abcdefghijklmnop"), out);
        assertTrue(out.contains("[SECRET]") && !out.contains("abc123"), out);
    }

    @Test
    void configDropsWebhookAndSecrets() {
        var json = JsonParser.parseString("{\"webhookEnabled\":true,\"webhookOnPanic\":true,\"apiKey\":\"k\","
                + "\"note\":\"see https://x.example/path\",\"panicEnabled\":true}");
        String out = BugReport.portableConfig(json);
        assertFalse(out.contains("webhook"), out);
        assertFalse(out.contains("apiKey"), out);
        assertTrue(out.contains("[URL]"), out);
        assertTrue(out.contains("panicEnabled"), out);
    }
}

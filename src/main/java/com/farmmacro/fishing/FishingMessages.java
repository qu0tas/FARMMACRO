package com.farmmacro.fishing;

import com.farmmacro.FarmMacroMod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Canonical English GAME-message allowlist; never player CHAT or actionbar substring matching. */
public final class FishingMessages {
    private static Set<String> messages = Set.of();

    public static void load() {
        try (var stream = FishingMessages.class.getResourceAsStream("/assets/farmmacro/fishing/sea-creatures.txt")) {
            if (stream == null) throw new IOException("Missing fishing messages");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                var result = new HashSet<String>();
                reader.lines()
                        .limit(256)
                        .map(FishingMessages::clean)
                        .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                        .forEach(result::add);
                messages = Set.copyOf(result);
            }
        } catch (IOException e) {
            FarmMacroMod.LOGGER.warn("Fishing creature messages unavailable", e);
            messages = Set.of();
        }
    }

    public static String clean(String s) {
        return s == null ? "" : s.replaceAll("§.", "").strip();
    }

    public static boolean creature(String s) {
        return messages.contains(clean(s));
    }

    public static int size() {
        return messages.size();
    }

    private FishingMessages() {}
}

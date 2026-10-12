package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * v1.13 «Банвейв»: статистика наказаний Hypixel (GET https://api.hypixel.net/v2/punishmentstats, заголовок API-Key).
 * Опрос раз в {@link #POLL_MS} в фоне, пока детектор включён и есть ключ. Банов за окно = разница счётчиков
 * watchdog_total (+ staff_total) между самым свежим ответом и ответом на начало окна.
 *
 * Ключ хранится отдельно — config/farmmacro/hypixel-api-key.txt — и в профили настроек не попадает.
 * Никаких других адресов и данных об игроке: только этот запрос.
 */
public final class BanwaveWatch {
    private BanwaveWatch() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Banwave");
    public static final String URL = "https://api.hypixel.net/v2/punishmentstats";
    public static final long POLL_MS = 60_000, RETRY_MS = 120_000;

    /** Ответ API в момент ms. */
    public record Sample(long ms, long watchdogTotal, long staffTotal) {}

    private static final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private static volatile String status = "выключен";
    private static volatile long nextPollMs;
    private static volatile boolean inFlight;
    private static String key;
    private static HttpClient client;

    // ── Чистая математика (проверка — BanwaveCheck) ──────────────────────────

    /**
     * Банов за последние windowMs: свежий ответ минус ответ на начало окна (или самый старый, если окно ещё не набрано).
     * @return −1 — меньше двух ответов
     */
    public static long bansIn(List<Sample> list, long nowMs, long windowMs, boolean withStaff) {
        if (list.size() < 2) return -1;
        Sample last = list.get(list.size() - 1), base = list.get(0);
        for (Sample s : list) {                       // последний ответ не позже начала окна
            if (s.ms() <= nowMs - windowMs) base = s; else break;
        }
        if (base == last) return 0;
        long d = last.watchdogTotal() - base.watchdogTotal();
        if (withStaff) d += last.staffTotal() - base.staffTotal();
        return Math.max(0, d);
    }

    /** Разбор ответа API. @return null — success=false или нет полей */
    public static Sample parse(String json, long ms) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (!o.has("success") || !o.get("success").getAsBoolean()) return null;
        if (!o.has("watchdog_total") || !o.has("staff_total")) return null;
        return new Sample(ms, o.get("watchdog_total").getAsLong(), o.get("staff_total").getAsLong());
    }

    // ── Ключ ─────────────────────────────────────────────────────────────────

    private static Path keyFile() { return ModConfig.modDir().resolve("hypixel-api-key.txt"); }

    public static synchronized String key() {
        if (key == null) {
            try { key = Files.exists(keyFile()) ? Files.readString(keyFile(), StandardCharsets.UTF_8).strip() : ""; }
            catch (Exception e) { LOGGER.warn("Не удалось прочитать ключ Hypixel", e); key = ""; }
        }
        return key;
    }

    /** @return false — ключ не похож на UUID */
    public static synchronized boolean setKey(String k) {
        k = k == null ? "" : k.strip();
        if (!k.isEmpty() && !k.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) return false;
        try {
            Files.createDirectories(keyFile().getParent());
            Files.writeString(keyFile(), k, StandardCharsets.UTF_8);
        } catch (Exception e) { LOGGER.warn("Не удалось сохранить ключ Hypixel", e); }
        key = k;
        synchronized (samples) { samples.clear(); }
        nextPollMs = 0;
        return true;
    }

    // ── Опрос ────────────────────────────────────────────────────────────────

    /** Каждый тик из главного потока: пора — отправить запрос в фоне. */
    public static void tick() {
        ModConfig c = ModConfig.INSTANCE;
        if (!c.detectBanwave) { status = "выключен"; return; }
        String k = key();
        if (k.isEmpty()) { status = "нет API-ключа"; return; }
        long now = System.currentTimeMillis();
        if (inFlight || now < nextPollMs) return;
        inFlight = true;
        nextPollMs = now + POLL_MS;
        if (status != null && !status.startsWith("собираю")) status = "жду ответ API";
        if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(15))
                .header("API-Key", k).header("User-Agent", "FarmMacro").GET().build();
        CompletableFuture<HttpResponse<String>> f = client.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        f.whenComplete((resp, err) -> {
            try {
                long t = System.currentTimeMillis();
                if (err != null) { status = "нет связи с api.hypixel.net"; nextPollMs = t + RETRY_MS; return; }
                int code = resp.statusCode();
                if (code == 403) { status = "ключ не подходит (403)"; nextPollMs = t + RETRY_MS * 5; return; }
                if (code == 429) { status = "лимит запросов (429)"; nextPollMs = t + RETRY_MS; return; }
                if (code != 200) { status = "ошибка API " + code; nextPollMs = t + RETRY_MS; return; }
                Sample s = parse(resp.body(), t);
                if (s == null) { status = "непонятный ответ API"; return; }
                synchronized (samples) {
                    samples.addLast(s);
                    while (samples.size() > 2 && samples.peekFirst().ms() < t - 3_600_000L) samples.pollFirst();
                }
                status = null;
                String line = s.watchdogTotal() + " watchdog / " + s.staffTotal() + " staff (всего)";
                net.minecraft.client.Minecraft.getInstance().execute(() -> EventLog.log("BANWAVE", line));
            } catch (Exception e) {
                status = "ошибка разбора ответа";
                LOGGER.warn("Банвейв: ответ не разобран", e);
            } finally { inFlight = false; }
        });
    }

    /** Банов за окно из настроек; −1 — данных пока мало. */
    public static long bans() {
        ModConfig c = ModConfig.INSTANCE;
        List<Sample> copy;
        synchronized (samples) { copy = new ArrayList<>(samples); }
        return bansIn(copy, System.currentTimeMillis(), c.banwaveWindowMin * 60_000L, c.banwaveWithStaff);
    }

    /** Строка для меню и HUD. */
    public static String describe() {
        String s = status;
        if (s != null) return s;
        long b = bans();
        return b < 0 ? "собираю данные (нужно 2 ответа)" : b + " банов за " + ModConfig.INSTANCE.banwaveWindowMin + " мин";
    }
}

package com.farmmacro.notify;

import com.farmmacro.config.ModConfig;
import com.farmmacro.panic.EventLog;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * v1.14 «Discord-webhook» (выключен по умолчанию): при панике — сообщение «⚠ Паника: причина», время, сервер,
 * по галочке — скриншот. Отправка в своём фоновом потоке. Адрес хранится отдельно (config/farmmacro/discord-webhook.txt),
 * в профили настроек не попадает и нигде не пишется в лог (в журнал — только код ответа).
 */
public final class DiscordNotify {
    private DiscordNotify() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Discord");
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FarmMacro-Discord");
        t.setDaemon(true);
        return t;
    });
    private static volatile String status = "";
    private static String url;
    private static HttpClient client;

    private static Path file() { return ModConfig.modDir().resolve("discord-webhook.txt"); }

    public static synchronized String url() {
        if (url == null) {
            try { url = Files.exists(file()) ? Files.readString(file(), StandardCharsets.UTF_8).strip() : ""; }
            catch (Exception e) { LOGGER.warn("Не удалось прочитать файл вебхука ({})", e.getClass().getSimpleName()); url = ""; }
        }
        return url;
    }

    /** Поле в меню: показываем без токена. */
    public static String maskedUrl() { return Webhook.masked(url()); }

    /** Из меню. Маска без изменений — оставить как есть; пусто — удалить. @return false — не похоже на адрес вебхука */
    public static synchronized boolean setUrl(String v) {
        String s = v == null ? "" : v.strip();
        if (s.equals(maskedUrl()) && !s.isEmpty()) return true;
        if (!s.isEmpty() && !Webhook.validUrl(s)) return false;
        url = s;
        try {
            Files.createDirectories(file().getParent());
            if (s.isEmpty()) Files.deleteIfExists(file());
            else Files.writeString(file(), s, StandardCharsets.UTF_8);
            status = s.isEmpty() ? "адрес удалён" : "адрес сохранён";
        } catch (Exception e) {
            LOGGER.warn("Не удалось сохранить адрес вебхука ({})", e.getClass().getSimpleName());
            status = "не удалось сохранить адрес";
        }
        return true;
    }

    public static boolean configured() { return Webhook.validUrl(url()); }
    public static String status() { return status; }

    /** Отправить текст и (если есть) PNG. Не блокирует игру. */
    public static void send(String content, Path png, String why) {
        String u = url();
        if (!Webhook.validUrl(u)) { status = "нет адреса вебхука"; return; }
        status = "отправляю…";
        POOL.execute(() -> {
            try {
                if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                String json = Webhook.payload(content);
                HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(20));
                byte[] bytes = png != null && Files.exists(png) && Files.size(png) < 8_000_000 ? Files.readAllBytes(png) : null;
                if (bytes != null) {
                    String boundary = "FarmMacro" + Long.toHexString(System.nanoTime());
                    rb.header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(Webhook.multipart(boundary, json, png.getFileName().toString(), bytes)));
                } else {
                    rb.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
                }
                HttpResponse<Void> r = client.send(rb.build(), HttpResponse.BodyHandlers.discarding());
                int code = r.statusCode();
                status = code / 100 == 2 ? "отправлено (" + why + ")" : "ошибка: ответ " + code;
                EventLog.log("DISCORD", why + ": ответ " + code + (bytes != null ? ", со скриншотом" : ""));
            } catch (Exception e) {
                status = "нет связи с Discord (" + e.getClass().getSimpleName() + ")";
                EventLog.log("DISCORD", why + ": ошибка " + e.getClass().getSimpleName());
            }
        });
    }
}

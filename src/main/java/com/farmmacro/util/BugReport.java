package com.farmmacro.util;

import com.farmmacro.config.ModConfig;
import com.farmmacro.notify.PrivateText;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.NullMarked;

/**
 * 2.1.0: «ZIP для багрепорта» — один файл, который можно приложить к issue.
 *
 * <p>Внутри: технический отчёт {@link Diagnostics}, список модов, конфиг без секретов (как в «строке
 * настроек»: webhook, токены, пароли выброшены, ссылки замаскированы) и хвост {@code logs/latest.log} с
 * замаскированными ником, ссылками, IP, e-mail, UUID и домашней папкой. Скриншоты, журнал паники, маршруты и
 * {@code discord-webhook.txt} не входят. Файл пишется в {@code config/farmmacro/bugreports/}, в сеть ничего не уходит.
 */
@NullMarked
public final class BugReport {
    static final int LOG_TAIL_BYTES = 2 * 1024 * 1024;
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern IP =
            Pattern.compile("(?<![0-9.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?(?![0-9])");
    private static final Pattern UUID =
            Pattern.compile("(?i)\\b[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}\\b");
    private static volatile String status = "";

    private BugReport() {}

    public static String status() {
        return status;
    }

    public static Path dir() {
        return ModConfig.modDir().resolve("bugreports");
    }

    /** Маскировка текста журнала. Чистая функция — проверяется JUnit. */
    static String redact(String text, List<String> names, String home) {
        String s = text;
        if (home.length() >= 3) s = s.replace(home, "~");
        for (String n : names)
            if (n.length() >= 3)
                s = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(n) + "(?![A-Za-z0-9_])")
                        .matcher(s)
                        .replaceAll("[PLAYER]");
        s = PrivateText.secrets(s);
        s = EMAIL.matcher(s).replaceAll("[EMAIL]");
        s = UUID.matcher(s).replaceAll("[UUID]");
        s = IP.matcher(s).replaceAll("[IP]");
        return s;
    }

    /** Конфиг для отчёта: те же правила, что у «строки настроек», плюс без всех webhook*-полей. */
    static String portableConfig(JsonElement config) {
        JsonElement p = PrivateText.portable(config);
        if (p instanceof JsonObject o) {
            for (String k : List.copyOf(o.keySet())) if (k.startsWith("webhook")) o.remove(k);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(p);
    }

    /** Собрать отчёт: данные с потока игры — здесь, запись ZIP — в фоне ({@link IoWorker}). */
    public static void create(Minecraft mc) {
        String diagnostics = Diagnostics.report(mc);
        String config = portableConfig(new Gson().toJsonTree(ModConfig.INSTANCE));
        List<String> names = new ArrayList<>();
        if (mc.getUser() != null) names.add(mc.getUser().getName());
        String home = System.getProperty("user.home", "");
        Path log = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
        Path out = dir().resolve("kaelix-bugreport-" + stamp + ".zip");
        status = "Собираю отчёт…";
        IoWorker.submitLatest("bugreport", () -> {
            try {
                write(out, log, diagnostics, config, names, home);
            } catch (IOException | RuntimeException e) {
                status = "Отчёт не записан (" + e.getClass().getSimpleName() + "), повторяю…";
                throw e;
            }
            status = "Готово: " + out.getFileName() + " (папка bugreports)";
        });
    }

    private static void write(Path out, Path log, String diagnostics, String config, List<String> names, String home)
            throws IOException {
        Files.createDirectories(out.getParent());
        String logText = readTail(log);
        try (OutputStream fo = Files.newOutputStream(out);
                ZipOutputStream zip = new ZipOutputStream(fo, StandardCharsets.UTF_8)) {
            put(zip, "README.txt", readme());
            put(zip, "diagnostics.txt", redact(diagnostics, names, home));
            put(zip, "mods.txt", mods());
            put(zip, "farmmacro.json", redact(config, names, home));
            put(zip, "latest.log", redact(logText, names, home));
        }
    }

    private static String readTail(Path log) throws IOException {
        if (!Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS)) return "(logs/latest.log не найден)";
        byte[] all = Files.readAllBytes(log);
        int from = Math.max(0, all.length - LOG_TAIL_BYTES);
        String s = new String(all, from, all.length - from, StandardCharsets.UTF_8);
        return from > 0 ? "(… начало журнала обрезано, последние " + LOG_TAIL_BYTES / 1024 + " КБ)\n" + s : s;
    }

    private static String mods() {
        StringBuilder b = new StringBuilder();
        FabricLoader.getInstance().getAllMods().stream()
                .map(m -> m.getMetadata().getId() + " "
                        + m.getMetadata().getVersion().getFriendlyString())
                .sorted()
                .forEach(line -> b.append(line).append('\n'));
        b.append("\njava ")
                .append(Runtime.version())
                .append(" / ")
                .append(System.getProperty("os.name"))
                .append(' ')
                .append(System.getProperty("os.arch"));
        return b.toString();
    }

    private static String readme() {
        return """
                Kaelix bug report
                diagnostics.txt — технический отчёт (как «Скопировать общий отчёт»)
                mods.txt        — моды и версии, Java, ОС
                farmmacro.json  — настройки без webhook/токенов/паролей, ссылки замаскированы
                latest.log      — хвост журнала игры; ник, ссылки, IP, e-mail, UUID и домашняя папка замаскированы
                Не входят: скриншоты, журнал паники, маршруты, discord-webhook.txt.
                Перед отправкой всё равно просмотри файлы — маскировка не гарантирует 100%.
                """;
    }

    private static void put(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

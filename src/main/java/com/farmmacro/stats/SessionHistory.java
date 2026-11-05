package com.farmmacro.stats;

import com.farmmacro.config.ModConfig;
import com.farmmacro.hud.StatsHud;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.util.AtomicFiles;
import com.farmmacro.util.Clock;
import com.farmmacro.util.IoWorker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * 2.1.0: история запусков макроса — длительность, культура, маршрут, монеты и Farming XP за запуск — и итог
 * «монет в час» по культурам. Хранится в памяти (последние {@value #MAX_ROWS}); на диск
 * ({@code config/farmmacro/sessions.csv}) — только при включённых «Daily / lifetime локально», как и
 * остальные итоги. Ников, UUID, серверов и координат здесь нет.
 */
@NullMarked
public final class SessionHistory {
    static final int MAX_ROWS = 500;
    static final String HEADER = "started,duration_min,crop,route,net_coins,coins_per_hour,farming_xp,loops,end";
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    /** Один запуск. {@code net}/{@code xp} — NaN, если за запуск не было наблюдений. */
    public record Run(
            long startedAt, long durationMs, String crop, String route, double net, double xp, int loops, String end) {
        public double coinsPerHour() {
            return Double.isFinite(net) && durationMs >= 60_000 ? net * 3_600_000d / durationMs : Double.NaN;
        }
    }

    /** Сумма по культуре. */
    public record CropTotal(String crop, int runs, long durationMs, double net, double xp) {
        public double coinsPerHour() {
            return durationMs >= 60_000 ? net * 3_600_000d / durationMs : Double.NaN;
        }
    }

    private static final Object LOCK = new Object();
    private static final List<Run> RUNS = new ArrayList<>();
    private static boolean loaded;
    private static long startWall, startMono;
    private static double startNet = Double.NaN, startXp = Double.NaN;
    private static String crop = "—", route = "—";
    private static boolean active;

    private SessionHistory() {}

    // ── Чистая логика (JUnit) ─────────────────────────────────────────────

    /** Строка CSV (RFC 4180: запятые/кавычки/переводы строк — в кавычках). */
    static String csvLine(Run r) {
        return String.join(
                ",",
                TIME.format(Instant.ofEpochMilli(r.startedAt())),
                fmt(r.durationMs() / 60_000d, 1),
                quote(r.crop()),
                quote(r.route()),
                fmt(r.net(), 0),
                fmt(r.coinsPerHour(), 0),
                fmt(r.xp(), 1),
                Integer.toString(r.loops()),
                quote(r.end()));
    }

    static String csv(List<Run> runs) {
        StringBuilder b = new StringBuilder(HEADER).append('\n');
        for (Run r : runs) b.append(csvLine(r)).append('\n');
        return b.toString();
    }

    /** Итог по культурам, в порядке первого появления. Запуски без наблюдённых монет в сумму монет не идут. */
    static List<CropTotal> byCrop(List<Run> runs) {
        Map<String, CropTotal> m = new LinkedHashMap<>();
        for (Run r : runs) {
            if (!Double.isFinite(r.net())) continue;
            CropTotal t = m.get(r.crop());
            m.put(
                    r.crop(),
                    t == null
                            ? new CropTotal(r.crop(), 1, r.durationMs(), r.net(), finite(r.xp()))
                            : new CropTotal(
                                    r.crop(),
                                    t.runs() + 1,
                                    t.durationMs() + r.durationMs(),
                                    t.net() + r.net(),
                                    t.xp() + finite(r.xp())));
        }
        return List.copyOf(m.values());
    }

    /** Разница счётчика за запуск; счётчик сбросили посреди запуска — берём новое значение. */
    static double delta(double start, double end) {
        if (!Double.isFinite(end)) return Double.NaN;
        if (!Double.isFinite(start)) return end;
        return end >= start ? end - start : end;
    }

    static String quote(String s) {
        String clean = s.replace('\r', ' ').replace('\n', ' ');
        boolean formula = !clean.isEmpty() && "=+-@".indexOf(clean.charAt(0)) >= 0; // CSV injection в Excel
        if (formula) clean = "'" + clean;
        return clean.contains(",") || clean.contains("\"") || formula
                ? "\"" + clean.replace("\"", "\"\"") + "\""
                : clean;
    }

    private static String fmt(double v, int digits) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%." + digits + "f", v) : "";
    }

    private static double finite(double v) {
        return Double.isFinite(v) ? v : 0;
    }

    // ── Игра ────────────────────────────────────────────────────────────

    /** Начало запуска (MacroManager → PLAYING). */
    public static void begin(@Nullable String cropLabel, @Nullable String routeName) {
        synchronized (LOCK) {
            ensureLoaded();
            active = true;
            startWall = Clock.wallMillis();
            startMono = Clock.millis();
            startNet = Telemetry.receipts() > 0 ? Telemetry.income() - Telemetry.expense() : Double.NaN;
            startXp = Telemetry.FARMING.gained();
            crop = cropLabel == null || cropLabel.isBlank() ? "—" : cropLabel;
            route = routeName == null || routeName.isBlank() ? "—" : routeName;
        }
    }

    /** Конец запуска (PLAYING → IDLE). {@code end}: finish / stop / lost-world / force. */
    public static void end(String end) {
        Run r;
        synchronized (LOCK) {
            if (!active) return;
            active = false;
            double endNet = Telemetry.receipts() > 0 ? Telemetry.income() - Telemetry.expense() : Double.NaN;
            r = new Run(
                    startWall,
                    Math.max(0, Clock.millis() - startMono),
                    crop,
                    route,
                    delta(startNet, endNet),
                    delta(startXp, Telemetry.FARMING.gained()),
                    MacroManager.INSTANCE.getLoopsDone(),
                    end);
            RUNS.add(r);
            while (RUNS.size() > MAX_ROWS) RUNS.removeFirst();
        }
        persist();
    }

    public static List<Run> runs() {
        synchronized (LOCK) {
            ensureLoaded();
            return List.copyOf(RUNS);
        }
    }

    public static List<CropTotal> byCrop() {
        return byCrop(runs());
    }

    public static String csv() {
        return csv(runs());
    }

    public static Path file() {
        return ModConfig.modDir().resolve("sessions.csv");
    }

    public static void clear() {
        synchronized (LOCK) {
            RUNS.clear();
            loaded = true;
        }
        persist();
    }

    /** Краткая строка для меню: по культурам, монет в час. */
    public static String summary() {
        List<CropTotal> totals = byCrop();
        if (totals.isEmpty()) return "Запусков с наблюдёнными монетами пока нет";
        StringBuilder b = new StringBuilder();
        for (CropTotal t : totals) {
            if (!b.isEmpty()) b.append(" • ");
            b.append(t.crop())
                    .append(": ")
                    .append(StatsHud.num(t.coinsPerHour()))
                    .append("/ч (")
                    .append(t.runs())
                    .append(" зап., ")
                    .append(t.durationMs() / 60_000)
                    .append(" мин)");
        }
        return b.toString();
    }

    private static void persist() {
        if (!ModConfig.INSTANCE.telemetry.save) return;
        Path f = file();
        String text = csv();
        IoWorker.submitLatest("sessions.csv", () -> {
            Files.createDirectories(f.getParent());
            if (Files.isSymbolicLink(f)) throw new IOException("symlink");
            AtomicFiles.writeUtf8(f, w -> w.write(text));
        });
    }

    /** Прочитать прежний CSV (только чтобы не затереть историю; разбор мягкий, битые строки пропускаются). */
    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!ModConfig.INSTANCE.telemetry.save) return;
        try {
            Path f = file();
            if (!Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS) || Files.size(f) > 2_000_000) return;
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                Run r = parse(lines.get(i));
                if (r != null) RUNS.add(r);
            }
            while (RUNS.size() > MAX_ROWS) RUNS.removeFirst();
        } catch (Exception ignored) {
            // нет файла или битый — начинаем пустую историю, файл перепишется при следующем запуске
        }
    }

    /** Обратный разбор {@link #csvLine}. */
    static @Nullable Run parse(String line) {
        List<String> f = splitCsv(line);
        if (f.size() != 9) return null;
        try {
            long started = LocalDateTime.parse(f.get(0), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            long duration = Math.round(Double.parseDouble(f.get(1)) * 60_000);
            double net = f.get(4).isEmpty() ? Double.NaN : Double.parseDouble(f.get(4));
            double xp = f.get(6).isEmpty() ? Double.NaN : Double.parseDouble(f.get(6));
            return new Run(
                    started,
                    duration,
                    unquoteFormula(f.get(2)),
                    unquoteFormula(f.get(3)),
                    net,
                    xp,
                    Integer.parseInt(f.get(7)),
                    unquoteFormula(f.get(8)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String unquoteFormula(String s) {
        return s.length() > 1 && s.charAt(0) == '\'' && "=+-@".indexOf(s.charAt(1)) >= 0 ? s.substring(1) : s;
    }

    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') quoted = false;
                else cur.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else cur.append(c);
        }
        out.add(cur.toString());
        return out;
    }
}

package com.farmmacro.panic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Проверка накопителя подозрительности и записи журнала событий (без Minecraft). */
public final class SuspicionCheck {
    private static void check(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException("SuspicionCheck: " + msg);
    }

    public static void main(String... args) throws Exception {
        double hl = 60;
        // полураспад
        Suspicion s = new Suspicion();
        s.add("лаг-откат", "0.05 бл", 2, 0, hl);
        check(Math.abs(s.score(0, hl) - 2) < 1e-9, "вес сразу");
        check(Math.abs(s.score(60_000, hl) - 1) < 1e-9, "половина через полураспад");
        check(Math.abs(s.score(120_000, hl) - 0.5) < 1e-9, "четверть через два");
        check(Math.abs(s.lastScore - 0.5) < 1e-9, "lastScore");
        // нулевой/отрицательный вес не добавляется
        s.add("x", "", 0, 0, hl);
        s.add("x", "", -1, 0, hl);
        s.add("x", "", Double.NaN, 0, hl);
        check(s.size() == 1, "нулевой вес игнорируется");
        // prune после 10 полураспадов
        s.score(601_000, hl);
        check(s.size() == 0, "старые события выкидываются");

        // порог: 5 откатов по 2 подряд = 10 → паника; редкие — нет
        s.reset();
        String r = null;
        for (int i = 0; i < 5; i++) {
            s.add("лаг-откат", "0.0" + i + " бл", 2, i * 100L, hl);
            r = s.reasonIfOver(i * 100L, hl, 10);
            if (i < 4) check(r == null, "рано паника на " + i);
        }
        check(r == null, "5×2 с затуханием чуть меньше 10");
        s.add("толчок", "0.3", 2, 500, hl);
        r = s.reasonIfOver(500, hl, 10);
        check(r != null, "должна быть паника");
        check(r.contains("лаг-откат ×5") && r.contains("(0.04 бл)") && r.contains("толчок ×1"), "текст: " + r);
        check(r.indexOf("лаг-откат") < r.indexOf("толчок"), "сортировка по вкладу: " + r);

        s.reset();
        for (int i = 0; i < 200; i++) {                       // раз в минуту по 2 — сумма ~4, не 10
            s.add("лаг-откат", "", 2, i * 60_000L, hl);
            check(s.reasonIfOver(i * 60_000L, hl, 10) == null, "редкие события не копятся до паники");
        }
        // лимит событий
        s.reset();
        for (int i = 0; i < 2000; i++) s.add("a", "", 0.001, 0, hl);
        check(s.size() == Suspicion.MAX_EVENTS, "лимит событий");

        // журнал: ротация
        Path dir = Files.createTempDirectory("fm-evlog");
        EventLogWriter w = new EventLogWriter(dir, 2048);
        LocalDate d = LocalDate.of(2026, 5, 1);
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 100; i++) lines.add(String.format("%03d строка журнала событий ............", i));
        w.write(lines, d);
        w.write(List.of("хвост"), d);
        long total = 0;
        int files = 0, count = 0;
        try (var st = Files.list(dir)) {
            for (Path p : st.toList()) {
                long sz = Files.size(p);
                check(sz <= 2048, "файл больше лимита: " + p + " " + sz);
                total += sz;
                files++;
                count += Files.readAllLines(p).size();
            }
        }
        check(files >= 3, "ротация не сработала: " + files);
        check(count == 101, "потеряны строки: " + count);
        check(Files.readAllLines(w.fileFor(d)).getLast().equals("хвост"), "последняя строка в текущем файле");
        check(Files.exists(dir.resolve("events-2026-05-01.1.log")), "имя архива");
        // строка длиннее лимита не зацикливает
        w.write(List.of("x".repeat(5000), "y"), d);
        try (var st = Files.list(dir)) { st.forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} }); }
        Files.delete(dir);
        System.out.println("SuspicionCheck OK (files=" + files + ", bytes=" + total + ")");
    }
}

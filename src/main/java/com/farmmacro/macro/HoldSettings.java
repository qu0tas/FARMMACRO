package com.farmmacro.macro;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * «Зажим мыши» макроса или маршрута: держать ЛКМ/ПКМ во время воспроизведения.
 *  • {@code whole}  — весь проход (после задержки старта);
 *  • {@code points} — запись: на участках кадров {@link Range}; маршрут: на отрезках от точек с галочкой
 *    {@code RoutePoint.hold}.
 * Задержка — тиков от начала воспроизведения (после обратного отсчёта), действует в обоих режимах.
 * Никакой случайности: кнопка зажата ровно там, где задано.
 */
public class HoldSettings {
    public static final String NONE = "none", ATTACK = "attack", USE = "use";
    public static final String WHOLE = "whole", POINTS = "points";
    public static final int DELAY_MAX = 20 * 60 * 60;    // час
    public static final int RANGES_MAX = 256;

    public String button = NONE;
    public String mode = WHOLE;
    public int delayTicks;
    /** Только для записей: участки кадров (0-based, включительно). */
    public List<Range> ranges = new ArrayList<>();

    public static class Range {
        public int from, to;
        public Range() {}
        public Range(int from, int to) { this.from = from; this.to = to; }
        public Range copy() { return new Range(from, to); }
    }

    public boolean active() { return !NONE.equals(button); }
    public boolean attack() { return ATTACK.equals(button); }
    public boolean use()    { return USE.equals(button); }

    /** Для записи в режиме points: кадр index внутри какого-то участка. */
    public boolean inRange(int index) {
        for (Range r : ranges) if (index >= Math.min(r.from, r.to) && index <= Math.max(r.from, r.to)) return true;
        return false;
    }

    /**
     * Держать ли кнопку (attack — ЛКМ, иначе ПКМ) на тике воспроизведения {@code runTicks} (1 — первый тик).
     * frame — кадр записи, pointHold — галочка точки текущего отрезка маршрута.
     */
    public boolean wants(boolean attackKey, int runTicks, boolean route, int frame, boolean pointHold) {
        if (attackKey ? !attack() : !use()) return false;
        if (runTicks <= delayTicks) return false;
        if (WHOLE.equals(mode)) return true;
        return route ? pointHold : inRange(frame);
    }

    public HoldSettings copy() {
        HoldSettings h = new HoldSettings();
        h.button = button; h.mode = mode; h.delayTicks = delayTicks;
        for (Range r : ranges) h.ranges.add(r.copy());
        return h;
    }

    /** Порядок участков по началу (перед записью в файл). */
    public void sort() {
        for (Range r : ranges) if (r.from > r.to) { int t = r.from; r.from = r.to; r.to = t; }
        ranges.sort(Comparator.comparingInt(r -> r.from));
    }

    /** После чтения файла (старые файлы — null). */
    public static HoldSettings sanitize(HoldSettings h) {
        if (h == null) return new HoldSettings();
        if (!NONE.equals(h.button) && !ATTACK.equals(h.button) && !USE.equals(h.button)) h.button = NONE;
        if (!WHOLE.equals(h.mode) && !POINTS.equals(h.mode)) h.mode = WHOLE;
        h.delayTicks = Math.max(0, Math.min(DELAY_MAX, h.delayTicks));
        if (h.ranges == null) h.ranges = new ArrayList<>();
        h.ranges.removeIf(Objects::isNull);
        for (Range r : h.ranges) { r.from = Math.max(0, r.from); r.to = Math.max(0, r.to); }
        while (h.ranges.size() > RANGES_MAX) h.ranges.remove(h.ranges.size() - 1);
        h.sort();
        return h;
    }

    public String buttonName() { return attack() ? "ЛКМ" : use() ? "ПКМ" : "нет"; }
}

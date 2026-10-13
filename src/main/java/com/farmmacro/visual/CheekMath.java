package com.farmmacro.visual;

import java.util.List;

/**
 * v1.16.1 «Анимешные щёчки»: стили, цвета и раскладка щёк на лице — без Minecraft (проверка: stress/visual/CheeksCheck).
 *
 * Координаты лица — пиксели передней грани головы для того, кто смотрит на лицо: x 0..8 слева направо, y 0..8 сверху вниз
 * (глаза Стива — строка 4, щёки — строки 5–6). Текстура — левая щека (внешний край слева), правая рисуется зеркально.
 */
public final class CheekMath {
    private CheekMath() {}

    public record Choice(String id, String label) {}

    public static final List<Choice> STYLES = List.of(
            new Choice("hearts",  "Сердечко ♥.."),
            new Choice("lines",   "Сердечко ♥///"),
            new Choice("stripes", "Штрихи ////"),
            new Choice("soft",    "Просто румянец"));

    public record Tint(String id, String label, int rgb) {}

    public static final List<Tint> COLORS = List.of(
            new Tint("pink",  "Розовые",    0xFF6F93),
            new Tint("red",   "Красные",    0xFF3F55),
            new Tint("peach", "Персиковые", 0xFF9A7A),
            new Tint("lilac", "Сиреневые",  0xC68BFF));

    public static final String DEF_STYLE = "hearts", DEF_COLOR = "pink";

    /** Размер щеки при 100 %, пиксели лица. */
    public static final float BASE_W = 2.7f, BASE_H = 1.35f;

    public static boolean isStyle(String id) { return STYLES.stream().anyMatch(s -> s.id().equals(id)); }
    public static boolean isColor(String id) { return COLORS.stream().anyMatch(c -> c.id().equals(id)); }

    public static int rgb(String id) {
        for (Tint t : COLORS) if (t.id().equals(id)) return t.rgb();
        return COLORS.get(0).rgb();
    }

    /** Текстура пятна (белая, окрашивается цветом). */
    public static String base(String style) { return "stripes".equals(style) ? "blush_stripes" : "blush_soft"; }

    /** Белые украшения поверх пятна или null. */
    public static String deco(String style) {
        return switch (style == null ? "" : style) {
            case "hearts" -> "deco_hearts";
            case "lines" -> "deco_lines";
            default -> null;
        };
    }

    /**
     * Щека, обрезанная по краю лица: {x0, y0, x1, y1, u0, v0, u1, v1} в пикселях лица и UV текстуры (0..1).
     * У правой щеки u идёт от 1 к 0 (зеркало), чтобы украшение было с внешней стороны. null — щека целиком вне лица.
     *
     * @param centerY  центр щёк по высоте, пиксели от макушки
     * @param spread   расстояние центра щеки от середины лица, пиксели
     * @param sizePct  размер, % от BASE_W×BASE_H
     */
    public static float[] rect(boolean left, double centerY, double spread, double sizePct) {
        float s = (float) (sizePct / 100.0);
        float w = BASE_W * s, h = BASE_H * s;
        float cx = (float) (left ? 4 - spread : 4 + spread), cy = (float) centerY;
        float x0 = cx - w / 2, x1 = cx + w / 2, y0 = cy - h / 2, y1 = cy + h / 2;
        float cx0 = Math.max(0, x0), cx1 = Math.min(8, x1), cy0 = Math.max(0, y0), cy1 = Math.min(8, y1);
        if (cx1 - cx0 <= 1e-4f || cy1 - cy0 <= 1e-4f) return null;
        float u0 = (cx0 - x0) / w, u1 = (cx1 - x0) / w;
        if (!left) { u0 = 1 - u0; u1 = 1 - u1; }
        float v0 = (cy0 - y0) / h, v1 = (cy1 - y0) / h;
        return new float[]{cx0, cy0, cx1, cy1, u0, v0, u1, v1};
    }

    /** Непрозрачность 0..1: из настройки (%), с лёгкой «пульсацией» по времени (тики), если включена. */
    public static float alpha(int opacityPct, boolean pulse, float ageTicks) {
        float a = Math.max(0, Math.min(100, opacityPct)) / 100f;
        if (pulse) a *= 0.78f + 0.22f * (0.5f + 0.5f * (float) Math.sin(ageTicks * 0.12f));
        return a;
    }
}

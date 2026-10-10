package com.farmmacro.visual;

import java.util.List;

/** Пресеты цветов шляпы и расчёт цвета по углу. Цвета — 0xRRGGBB. */
public final class HatColors {
    private HatColors() {}

    public record Preset(String id, String label, int rgb) {}

    public static final List<Preset> PRESETS = List.of(
            new Preset("purple", "Фиолетовый", 0x9B30FF),
            new Preset("orange", "Оранжевый", 0xFF8A1F),
            new Preset("cyan",   "Голубой",    0x38D6FF),
            new Preset("pink",   "Розовый",    0xFF4FB3),
            new Preset("red",    "Красный",    0xFF3344),
            new Preset("yellow", "Жёлтый",     0xFFE14D),
            new Preset("green",  "Зелёный",    0x4DFF88),
            new Preset("blue",   "Синий",      0x3D6BFF),
            new Preset("white",  "Белый",      0xF2F2F2),
            new Preset("gray",   "Серый",      0x8A8A8A),
            new Preset("dark_gray", "Тёмно-серый", 0x3A3A3A),
            new Preset("black",  "Чёрный",     0x000000));

    /** Id «как задумано в моде» для настроек цвета отдельных элементов (точки, маяки, HUD…). */
    public static final String DEFAULT = "default";

    /** Цвет пресета или def, если выбран «По умолчанию» / неизвестный id. */
    public static int rgbOr(String id, int def) {
        for (Preset p : PRESETS) if (p.id().equals(id)) return p.rgb();
        return def;
    }

    /** Яркость 0–255 (для тёмных цветов: светлая середина ленты не должна делать чёрное серым). */
    public static int luma(int rgb) {
        return (((rgb >> 16) & 255) * 3 + ((rgb >> 8) & 255) * 6 + (rgb & 255)) / 10;
    }

    public static final List<String[]> STYLES = List.of(
            new String[]{"gradient", "Градиент"},
            new String[]{"solid", "Однотонный"},
            new String[]{"rainbow", "Перелив"});

    public static int rgb(String id) {
        for (Preset p : PRESETS) if (p.id().equals(id)) return p.rgb();
        return 0x9B30FF;
    }

    public static boolean isPreset(String id) {
        for (Preset p : PRESETS) if (p.id().equals(id)) return true;
        return false;
    }

    /** Цвет точки на окружности t∈[0,1). Градиент идёт c1 → c2 → c1, чтобы не было шва. */
    public static int at(String style, int c1, int c2, double t, double hueShift) {
        return switch (style) {
            case "solid" -> c1;
            case "rainbow" -> hsv((float) ((t + hueShift) % 1.0), 0.75f, 1f);
            default -> mix(c1, c2, (float) ((1 - Math.cos(t * Math.PI * 2)) / 2));
        };
    }

    public static int mix(int a, int b, float k) {
        int r = Math.round(((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k);
        int g = Math.round(((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k);
        int bl = Math.round((a & 255) * (1 - k) + (b & 255) * k);
        return (r << 16) | (g << 8) | bl;
    }

    public static int lighten(int c, float k) { return mix(c, 0xFFFFFF, k); }

    private static int hsv(float h, float s, float v) {
        return net.minecraft.util.Mth.hsvToRgb(h, s, v) & 0xFFFFFF;
    }
}

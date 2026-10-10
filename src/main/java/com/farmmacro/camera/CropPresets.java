package com.farmmacro.camera;

import com.farmmacro.config.ModConfig.CamPreset;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * v1.11: культуры и стандартные пресеты камеры под них. Без зависимостей от игры (проверяется в routeStress).
 * Стандартные пресеты — обычные пресеты {@link CameraPresets} (можно переименовать, поменять yaw/pitch, удалить).
 * Углы хранятся с точностью 0.1°: yaw −180…180, pitch −90…90.
 */
public final class CropPresets {
    private CropPresets() {}

    public record Crop(String id, String label) {}

    /** Культуры для выбора в пресете и в «Запуск → Культура». Старые id (wart, cane, cocoa …) сохранены. */
    public static final List<Crop> CROPS = List.of(
            new Crop("wart", "Nether Wart"), new Crop("wheat", "Wheat"), new Crop("potato", "Potato"),
            new Crop("carrot", "Carrot"), new Crop("melon", "Melon"), new Crop("pumpkin", "Pumpkin"),
            new Crop("moonflower", "Moonflower"), new Crop("cane", "Sugar Cane"), new Crop("wild_rose", "Wild Rose"),
            new Crop("sunflower", "Sunflower"), new Crop("mushroom", "Mushroom"), new Crop("cactus", "Cactus"),
            new Crop("cocoa", "Cocoa Beans"), new Crop("other", "Другое"));

    public static final String OTHER = "other";
    /** «Запуск → Культура»: не применять пресет при старте. */
    public static final String NONE = "none";

    /** Стандартный пресет: имя, культура (подпись), yaw, pitch и все культуры, для которых он подходит. */
    public record Std(String name, String crop, float yaw, float pitch, List<String> crops) {}

    public static final List<Std> STANDARD = List.of(
            new Std("Nether Wart / Wheat", "wart", 135f, 0f, List.of("wart", "wheat")),
            new Std("Potato / Carrot", "potato", 135f, 5f, List.of("potato", "carrot")),
            new Std("Melon / Pumpkin", "melon", 135f, -40f, List.of("melon", "pumpkin")),
            new Std("Moonflower / Sugar Cane / Wild Rose / Sunflower", "moonflower", 135f, 0f,
                    List.of("moonflower", "cane", "wild_rose", "sunflower")),
            new Std("Mushroom", "mushroom", 165f, 0f, List.of("mushroom")),
            new Std("Cactus", "cactus", -157.9f, -17.4f, List.of("cactus")),
            new Std("Cocoa Beans", "cocoa", 90f, -30f, List.of("cocoa")));

    /** Ревизия набора стандартных пресетов: при росте — дописать новые (без дублей по имени). */
    public static final int DEFAULTS_REV = 1;

    public static boolean isCrop(String id) {
        if (id == null) return false;
        for (Crop c : CROPS) if (c.id().equals(id)) return true;
        return false;
    }

    public static String label(String id) {
        for (Crop c : CROPS) if (c.id().equals(id)) return c.label();
        return "Другое";
    }

    /** Округление до 0.1° (−0.0 → 0.0). */
    public static float round1(float v) {
        if (!Float.isFinite(v)) return 0f;
        float r = Math.round(v * 10.0) / 10f;
        return r == 0f ? 0f : r;
    }

    /** Yaw: −180…180, шаг 0.1° (как в F3: 270 → −90). */
    public static float yaw(float v) {
        if (!Float.isFinite(v)) return 0f;
        double w = ((v % 360.0) + 360.0) % 360.0;          // 0…360
        if (w > 180.0) w -= 360.0;                           // −180…180
        float r = round1((float) w);
        return r < -180f ? -180f : r > 180f ? 180f : r;
    }

    /** Pitch: −90…90, шаг 0.1°. */
    public static float pitch(float v) {
        if (!Float.isFinite(v)) return 0f;
        return round1(Math.max(-90f, Math.min(90f, v)));
    }

    private static boolean hasName(List<CamPreset> list, String name) {
        for (CamPreset p : list) if (p.name != null && p.name.strip().equalsIgnoreCase(name)) return true;
        return false;
    }

    private static CamPreset make(Std s, int id) {
        CamPreset p = new CamPreset(s.crop(), s.yaw(), s.pitch());
        p.id = id;
        p.name = s.name();
        return p;
    }

    /**
     * Миграция при загрузке: пусто → все стандартные; есть свои и {@code rev} &lt; {@link #DEFAULTS_REV} —
     * дописать стандартные, которых нет по имени (без учёта регистра), не больше {@code max}; свои не трогаются.
     * @return новая ревизия (если список изменился или ревизия выросла — конфиг нужно сохранить)
     */
    public static int ensureDefaults(List<CamPreset> list, int rev, int max, IntSupplier nextId) {
        if (list.isEmpty() || rev < DEFAULTS_REV) {
            for (Std s : STANDARD) {
                if (list.size() >= max) break;
                if (!hasName(list, s.name())) list.add(make(s, nextId.getAsInt()));
            }
        }
        return Math.max(rev, DEFAULTS_REV);
    }

    /**
     * «Сбросить к стандартным»: список = 7 стандартных. id совпавших по имени пресетов сохраняется
     * (на id ссылаются привязки камеры макросов и маршрутов).
     */
    public static List<CamPreset> resetToStandard(List<CamPreset> old, IntSupplier nextId) {
        List<CamPreset> out = new ArrayList<>();
        for (Std s : STANDARD) {
            int id = -1;
            for (CamPreset p : old) if (p.name != null && p.name.strip().equalsIgnoreCase(s.name())) { id = p.id; break; }
            out.add(make(s, id > 0 ? id : nextId.getAsInt()));
        }
        return out;
    }

    /**
     * Индекс пресета для культуры: сначала пресет, у которого выбрана эта культура; иначе стандартный пресет
     * её группы по имени (например, Wheat → «Nether Wart / Wheat»). -1 — нет.
     */
    public static int indexFor(List<CamPreset> list, String crop) {
        if (crop == null || NONE.equals(crop) || OTHER.equals(crop)) return -1;
        for (int i = 0; i < list.size(); i++) if (crop.equals(list.get(i).crop)) return i;
        for (Std s : STANDARD) {
            if (!s.crops().contains(crop)) continue;
            for (int i = 0; i < list.size(); i++)
                if (list.get(i).name != null && list.get(i).name.strip().equalsIgnoreCase(s.name())) return i;
        }
        return -1;
    }

    public static String describe(CamPreset p) {
        return String.format(Locale.ROOT, "%s · %s · yaw %.1f° · pitch %.1f°", p.name, label(p.crop), p.yaw, p.pitch);
    }
}

package com.farmmacro.camera;

import java.util.Locale;

/**
 * v1.16.3 Squeaky Mousemat (Hypixel Skyblock: ЛКМ — «Snap» к сохранённым yaw/pitch, ПКМ — записать), без Minecraft:
 * узнать предмет в хотбаре и проверить, что поворот от сервера — тот, что ждали. Проверка — MousematCheck.
 */
public final class MousematMath {
    private MousematMath() {}

    public static final String SKYBLOCK_ID = "SQUEAKY_MOUSEMAT";

    /** Это Mousemat: id Skyblock (custom_data → id) или имя содержит «Squeaky Mousemat» (без кодов §). */
    public static boolean isMat(String skyblockId, String name) {
        if (skyblockId != null && skyblockId.equalsIgnoreCase(SKYBLOCK_ID)) return true;
        if (name == null) return false;
        String n = name.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        return n.contains("squeaky mousemat");
    }

    /**
     * Слот хотбара (0–8) с Mousemat. wanted: 0 — любой (сначала текущий, потом слева направо), 1–9 — только этот слот.
     * mats[i] — в слоте i Mousemat. -1 — нет.
     */
    public static int pickSlot(boolean[] mats, int wanted, int current) {
        if (mats == null) return -1;
        if (wanted >= 1 && wanted <= 9) return wanted - 1 < mats.length && mats[wanted - 1] ? wanted - 1 : -1;
        if (current >= 0 && current < mats.length && mats[current]) return current;
        for (int i = 0; i < mats.length && i < 9; i++) if (mats[i]) return i;
        return -1;
    }

    /** Разница углов по yaw с переходом через ±180. */
    public static float yawDiff(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180f ? 360f - d : d;
    }

    /** Поворот совпал с ожидаемым (yaw и pitch не дальше tol градусов). */
    public static boolean matches(float yaw, float pitch, float wantYaw, float wantPitch, double tol) {
        return yawDiff(yaw, wantYaw) <= tol && Math.abs(pitch - wantPitch) <= tol;
    }
}

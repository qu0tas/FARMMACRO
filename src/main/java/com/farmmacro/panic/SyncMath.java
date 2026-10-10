package com.farmmacro.panic;

import java.util.Locale;

/**
 * v1.11: чистая логика детекторов «синхронизация с эпсилоном» и «параметры движения» — без зависимостей от игры
 * (проверяется в stress-тесте {@code SyncCheck}).
 */
public final class SyncMath {
    private SyncMath() {}

    /** Сдвиг/поворот из пакета сервера больше эпсилона? */
    public static boolean posOver(double dist, double eps) { return dist > eps; }
    public static boolean rotOver(double rot, double eps)  { return rot > eps; }

    /** Текст паники «синхронизация»: что именно разошлось и насколько. */
    public static String syncReason(String kind, double dist, double posEps, double dYaw, double dPitch, double rotEps) {
        boolean pos = posOver(dist, posEps), rot = rotOver(Math.max(dYaw, dPitch), rotEps);
        StringBuilder b = new StringBuilder("Сервер поправил ");
        if (pos) b.append(String.format(Locale.ROOT, "позицию на %.3f бл (> %.3f)", dist, posEps));
        if (pos && rot) b.append(" и ");
        if (rot) b.append(String.format(Locale.ROOT, "взгляд на yaw %.3f° / pitch %.3f° (> %.3f°)", dYaw, dPitch, rotEps));
        return b.append(" — пакет ").append(kind).toString();
    }

    /** Модификатор атрибута: amount + операция 0 = ADD_VALUE, 1 = ADD_MULTIPLIED_BASE, 2 = ADD_MULTIPLIED_TOTAL. */
    public record Mod(String id, double amount, int op) {}

    /**
     * Значение атрибута как в {@code AttributeInstance.calculateValue} (без sanitize): base + Σ add,
     * затем + v·Σ mult_base, затем × Π(1 + mult_total). Модификаторы, у которых {@link #isTransient} — пропускаются.
     */
    public static double value(double base, Iterable<Mod> mods) {
        double v = base;
        if (mods != null) for (Mod m : mods) if (m.op == 0 && !isTransient(m.id)) v += m.amount;
        double r = v;
        if (mods != null) for (Mod m : mods) if (m.op == 1 && !isTransient(m.id)) r += v * m.amount;
        if (mods != null) for (Mod m : mods) if (m.op == 2 && !isTransient(m.id)) r *= 1 + m.amount;
        return r;
    }

    /**
     * Модификаторы, которые клиент считает сам и которые расходятся с сервером на тик-другой без участия админа:
     * спринт, замерзание в снегу, скорость душ на песке душ. Их сравнивать нельзя — будет ложная паника на старте бега.
     */
    public static boolean isTransient(String id) {
        if (id == null) return false;
        return id.contains("sprint") || id.contains("powder_snow") || id.contains("soul_speed");
    }

    /** Относительное изменение |b − a| / max(|a|, 1e-6), в процентах. */
    public static double relPct(double a, double b) {
        return Math.abs(b - a) / Math.max(Math.abs(a), 1e-6) * 100.0;
    }

    /** Изменилось больше порога (в %)? */
    public static boolean changed(double a, double b, double epsPct) { return relPct(a, b) > epsPct; }

    /** Подпись атрибута для сообщения о панике (путь id: movement_speed → «скорость»). */
    public static String attrLabel(String path) {
        if (path == null) return "?";
        return switch (path) {
            case "movement_speed" -> "скорость";
            case "jump_strength"  -> "силу прыжка";
            case "gravity"        -> "гравитацию";
            case "step_height"    -> "высоту шага";
            case "scale"          -> "размер";
            default -> path;
        };
    }

    /**
     * Клетки блоков, через которые проходит луч из (ox, oy, oz) в направлении (dx, dy, dz) на длину range
     * (3D DDA, Amanatides–Woo). В consumer — x, y, z клетки и расстояние от начала луча до входа в неё.
     * Первая клетка — та, где начало луча (расстояние 0).
     */
    public interface CellConsumer { boolean accept(int x, int y, int z, double dist); }

    public static void cells(double ox, double oy, double oz, double dx, double dy, double dz, double range, CellConsumer c) {
        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (n < 1e-9 || !(range > 0)) return;
        dx /= n; dy /= n; dz /= n;
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tdx = sx != 0 ? Math.abs(1 / dx) : Double.POSITIVE_INFINITY;
        double tdy = sy != 0 ? Math.abs(1 / dy) : Double.POSITIVE_INFINITY;
        double tdz = sz != 0 ? Math.abs(1 / dz) : Double.POSITIVE_INFINITY;
        double tmx = sx > 0 ? (x + 1 - ox) * tdx : sx < 0 ? (ox - x) * tdx : Double.POSITIVE_INFINITY;
        double tmy = sy > 0 ? (y + 1 - oy) * tdy : sy < 0 ? (oy - y) * tdy : Double.POSITIVE_INFINITY;
        double tmz = sz > 0 ? (z + 1 - oz) * tdz : sz < 0 ? (oz - z) * tdz : Double.POSITIVE_INFINITY;
        double t = 0;
        for (int guard = 0; guard < 256 && t <= range; guard++) {
            if (!c.accept(x, y, z, t)) return;
            if (tmx <= tmy && tmx <= tmz) { t = tmx; x += sx; tmx += tdx; }
            else if (tmy <= tmz)          { t = tmy; y += sy; tmy += tdy; }
            else                          { t = tmz; z += sz; tmz += tdz; }
        }
    }
}

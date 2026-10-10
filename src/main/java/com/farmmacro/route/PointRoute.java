package com.farmmacro.route;

import com.farmmacro.macro.MacroSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Маршрут по точкам — содержимое файла config/farmmacro_routes/&lt;имя&gt;.json.
 * Версии: 1 — до 1.6.0 (было поле pitch, теперь игнорируется), 2 — привязка камеры {@code camera},
 * 3 (1.7.0) — все настройки в {@code settings} ({@link MacroSettings}); старое {@code camera} переносится при чтении,
 * 4 (1.8.0) — смещение точки в блоке {@code ox/oz} (x/z уже со смещением; в старых файлах 0 — центр),
 * 5 (1.9.0) — точка-«спуск» ({@code drop, dropDir, landPauseTicks, airHold, dropDepth}; в старых файлах — обычные точки).
 */
public class PointRoute {
    public static final int VERSION = 5;

    public int    version = VERSION;
    public String name;
    /** Где строился: адрес сервера или «singleplayer:&lt;мир&gt;» (только для подписи и предупреждения). */
    public String world;
    /** Измерение, например minecraft:overworld. */
    public String dimension;
    public long   createdAt;
    public List<RoutePoint> points = new ArrayList<>();
    /** Настройки маршрута (камера: from — индекс точки). */
    public MacroSettings settings = new MacroSettings();
    /** Только чтение файлов v2; после {@link #sanitize()} и в новых файлах — null. */
    public com.farmmacro.camera.CameraBinding camera;

    public PointRoute copy() {
        PointRoute r = new PointRoute();
        r.version = version; r.name = name; r.world = world; r.dimension = dimension;
        r.createdAt = createdAt;
        for (RoutePoint p : points) r.points.add(p.copy());
        r.settings = settings == null ? new MacroSettings() : settings.copy();
        return r;
    }

    public void sanitize() {
        if (points == null) points = new ArrayList<>();
        points.removeIf(p -> p == null || !p.finite());
        for (RoutePoint p : points) p.sanitize();
        settings = MacroSettings.fromFile(settings, camera);
        camera = null;
    }
}

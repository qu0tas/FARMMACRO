package com.farmmacro.route;

import java.util.ArrayList;
import java.util.List;

/** Маршрут по точкам — содержимое файла config/farmmacro_routes/&lt;имя&gt;.json. */
public class PointRoute {
    public static final int VERSION = 1;

    public int    version = VERSION;
    public String name;
    /** Где строился: адрес сервера или «singleplayer:&lt;мир&gt;» (только для подписи и предупреждения). */
    public String world;
    /** Измерение, например minecraft:overworld. */
    public String dimension;
    /** Pitch по умолчанию для всех отрезков, градусы. */
    public float  pitch = 0f;
    public long   createdAt;
    public List<RoutePoint> points = new ArrayList<>();

    public PointRoute copy() {
        PointRoute r = new PointRoute();
        r.version = version; r.name = name; r.world = world; r.dimension = dimension;
        r.pitch = pitch; r.createdAt = createdAt;
        for (RoutePoint p : points) r.points.add(p.copy());
        return r;
    }

    public void sanitize() {
        if (points == null) points = new ArrayList<>();
        points.removeIf(p -> p == null || !p.finite());
        for (RoutePoint p : points) p.sanitize();
        if (!Float.isFinite(pitch)) pitch = 0;
        pitch = Math.max(-90, Math.min(90, pitch));
    }

    /** Pitch на отрезке от точки i. */
    public float pitchAt(int i) {
        RoutePoint p = i >= 0 && i < points.size() ? points.get(i) : null;
        return p != null && p.pitch != null ? p.pitch : pitch;
    }
}

package com.farmmacro.route;

import com.farmmacro.config.ConfigCode;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NullMarked;

/**
 * 2.1.0: маршрут одной строкой для буфера обмена — {@code KAELIX-ROUTE1:} + base64url(gzip(JSON)), как
 * «строка настроек». Файл маршрута на диске не меняется; вставка кладёт маршрут в редактор несохранённым
 * (Ctrl+Z возвращает прежний).
 */
@NullMarked
public final class RouteCode {
    public static final String PREFIX = "KAELIX-ROUTE1:";
    private static final Gson GSON = new Gson();

    private RouteCode() {}

    /** Строка для копирования. Имя файла и мир/сервер не входят — только точки и настройки маршрута. */
    public static String encode(PointRoute route) {
        JsonObject o = GSON.toJsonTree(route).getAsJsonObject();
        o.remove("world");
        o.remove("dimension");
        return ConfigCode.encode(PREFIX, o.toString());
    }

    /**
     * Разобрать строку. @throws IllegalArgumentException с понятной причиной (не маршрут, пусто, слишком
     * много точек, нет ни одной точки с конечными координатами).
     */
    public static PointRoute decode(String code) {
        String json = ConfigCode.decode(PREFIX, code, "маршрута");
        PointRoute r;
        try {
            r = GSON.fromJson(JsonParser.parseString(json), PointRoute.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Строка маршрута повреждена (" + e.getClass().getSimpleName() + ")");
        }
        if (r == null) throw new IllegalArgumentException("Пустая строка маршрута");
        if (r.points != null && r.points.size() > RouteBuffer.MAX_POINTS)
            throw new IllegalArgumentException(
                    "Слишком много точек: " + r.points.size() + " (максимум " + RouteBuffer.MAX_POINTS + ")");
        r.sanitize();
        if (r.points.isEmpty()) throw new IllegalArgumentException("В строке нет ни одной точки");
        r.world = null;
        r.dimension = null;
        return r;
    }
}

package com.farmmacro.route;

import com.farmmacro.macro.MacroStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Файлы маршрутов по точкам: config/farmmacro_routes/*.json (формат {@link PointRoute}). */
public final class RouteStorage {
    public static final RouteStorage INSTANCE = new RouteStorage();
    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Routes");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("farmmacro_routes");

    public record RouteInfo(String name, int points, String dimension, String world, String filename, long modified) {}

    private final Map<String, RouteInfo> cache = new HashMap<>();

    private RouteStorage() {}

    private static Path fileFor(String name) {
        String safe = MacroStorage.sanitize(name);
        if (safe.isEmpty()) return null;
        try {
            return DIR.resolve(safe + ".json");
        } catch (InvalidPathException e) {
            return DIR.resolve("route_" + Integer.toHexString(name.trim().hashCode()) + ".json");
        }
    }

    public boolean exists(String name) {
        Path f = fileFor(name);
        return f != null && Files.exists(f);
    }

    public boolean save(String name, PointRoute route) {
        Path file = fileFor(name);
        if (file == null || route == null || route.points.isEmpty()) return false;
        try {
            Files.createDirectories(DIR);
            PointRoute r = route.copy();
            r.version = PointRoute.VERSION;
            r.name = name.trim();
            if (r.createdAt == 0) r.createdAt = System.currentTimeMillis();
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(r, w);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicNotSupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("Сохранён маршрут «{}» ({} точек) -> {}", r.name, r.points.size(), file.getFileName());
            return true;
        } catch (Exception e) {
            LOGGER.error("Не удалось сохранить маршрут «{}»", name, e);
            return false;
        }
    }

    public List<RouteInfo> list() {
        List<RouteInfo> out = new ArrayList<>();
        if (!Files.isDirectory(DIR)) return out;
        try (Stream<Path> s = Files.list(DIR)) {
            for (Path f : (Iterable<Path>) s.filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                String fn = f.getFileName().toString();
                long mod = Files.getLastModifiedTime(f).toMillis();
                RouteInfo info = cache.get(fn);
                if (info == null || info.modified() != mod) {
                    PointRoute r = read(f);
                    if (r == null) continue;
                    String name = r.name != null ? r.name : fn.substring(0, fn.length() - 5);
                    info = new RouteInfo(name, r.points.size(), r.dimension, r.world, fn, mod);
                    cache.put(fn, info);
                }
                out.add(info);
            }
        } catch (Exception e) {
            LOGGER.warn("Не удалось прочитать список маршрутов: {}", e.toString());
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /** @return маршрут или null (ошибка в логе) */
    public PointRoute load(String filename) {
        Path f = resolve(filename);
        if (f == null) return null;
        PointRoute r = read(f);
        if (r != null && r.points.isEmpty()) { LOGGER.warn("В маршруте {} нет точек", filename); return null; }
        return r;
    }

    private static PointRoute read(Path f) {
        try (Reader rd = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            PointRoute r = GSON.fromJson(rd, PointRoute.class);
            if (r == null) return null;
            if (r.version > PointRoute.VERSION)
                LOGGER.warn("Маршрут {} новее мода (версия {}), читаю что понимаю", f.getFileName(), r.version);
            r.sanitize();
            return r;
        } catch (Exception e) {
            LOGGER.error("Повреждён файл маршрута {}: {}", f.getFileName(), e.toString());
            return null;
        }
    }

    public boolean delete(String filename) {
        Path f = resolve(filename);
        if (f == null) return false;
        try {
            cache.remove(filename);
            return Files.deleteIfExists(f);
        } catch (Exception e) {
            LOGGER.error("Не удалось удалить {}", filename, e);
            return false;
        }
    }

    private static Path resolve(String filename) {
        Path p = DIR.resolve(filename).normalize();
        return p.startsWith(DIR) ? p : null;
    }
}

package com.farmmacro.macro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Именованные макросы: один файл .json на макрос в config/farmmacro_macros/.
 * Формат: {"name":"...","version":2,"createdAt":ms,"frameCount":N,"frames":[...]}.
 * Файлы версии 1 (только name + frames) читаются как раньше.
 * Список кешируется по времени изменения файла, чтобы меню не перечитывало большие файлы.
 */
public class MacroStorage {

    public static final MacroStorage INSTANCE = new MacroStorage();
    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Storage");
    private static final Gson GSON = new GsonBuilder().create();
    public static final Path MACRO_DIR = FabricLoader.getInstance().getConfigDir().resolve("farmmacro_macros");

    public static class SavedMacro {
        public String name;
        public int version = 2;
        public long createdAt;
        public int frameCount;
        public List<MacroFrame> frames;
    }

    public record MacroInfo(String name, int frameCount, String filename, long modified) {}

    private final Map<String, MacroInfo> infoCache = new HashMap<>();

    // ── Сохранение ───────────────────────────────────────────────────────────

    /** Есть ли уже макрос с таким именем (по имени файла). */
    public boolean exists(String name) {
        Path f = fileFor(name);
        return f != null && Files.exists(f);
    }

    /**
     * Файл для имени макроса. Если ОС не умеет такие символы в путях (Linux/macOS с не-UTF-8 локалью
     * и кириллицей), берём безопасное имя из хеша — настоящее имя всё равно хранится внутри файла.
     */
    private static Path fileFor(String name) {
        String safe = sanitize(name);
        if (safe.isEmpty()) return null;
        try {
            return MACRO_DIR.resolve(safe + ".json");
        } catch (java.nio.file.InvalidPathException e) {
            return MACRO_DIR.resolve("macro_" + Integer.toHexString(name.trim().hashCode()) + ".json");
        }
    }

    public boolean save(String name, List<MacroFrame> frames) {
        Path file = fileFor(name);
        if (file == null || frames.isEmpty()) return false;
        try {
            Files.createDirectories(MACRO_DIR);
            SavedMacro m = new SavedMacro();
            m.name = name.trim();
            m.createdAt = System.currentTimeMillis();
            m.frameCount = frames.size();
            m.frames = new ArrayList<>(frames);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(m, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Сохранён макрос «{}» ({} кадров) -> {}", m.name, m.frameCount, file.getFileName());
            return true;
        } catch (Exception e) {
            LOGGER.error("Не удалось сохранить макрос «{}»", name, e);
            return false;
        }
    }

    // ── Список ───────────────────────────────────────────────────────────────

    public List<MacroInfo> listMacros() {
        List<MacroInfo> result = new ArrayList<>();
        if (!Files.isDirectory(MACRO_DIR)) return result;
        try (Stream<Path> s = Files.list(MACRO_DIR)) {
            for (Path f : (Iterable<Path>) s.filter(p -> p.getFileName().toString().endsWith(".json"))::iterator) {
                String fn = f.getFileName().toString();
                long mod = Files.getLastModifiedTime(f).toMillis();
                MacroInfo cached = infoCache.get(fn);
                if (cached == null || cached.modified() != mod) {
                    cached = readInfo(f, fn, mod);
                    if (cached == null) continue;
                    infoCache.put(fn, cached);
                }
                result.add(cached);
            }
        } catch (Exception e) {
            LOGGER.warn("Не удалось прочитать список макросов: {}", e.toString());
        }
        result.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return result;
    }

    /** Читает имя и число кадров; для новых файлов — без разбора всех кадров. */
    private MacroInfo readInfo(Path f, String fn, long mod) {
        try (JsonReader r = new JsonReader(Files.newBufferedReader(f, StandardCharsets.UTF_8))) {
            String name = null;
            int count = -1;
            r.beginObject();
            while (r.hasNext()) {
                switch (r.nextName()) {
                    case "name" -> name = r.nextString();
                    case "frameCount" -> count = r.nextInt();
                    case "frames" -> {
                        if (count >= 0 && name != null) { r.skipValue(); }
                        else {
                            int n = 0;
                            r.beginArray();
                            while (r.hasNext()) { r.skipValue(); n++; }
                            r.endArray();
                            count = n;
                        }
                    }
                    default -> r.skipValue();
                }
                if (name != null && count >= 0) break;
            }
            if (name == null) name = fn.substring(0, fn.length() - 5);
            return new MacroInfo(name, Math.max(0, count), fn, mod);
        } catch (Exception e) {
            LOGGER.warn("Повреждён файл макроса {}: {}", fn, e.toString());
            return null;
        }
    }

    // ── Загрузка / удаление ──────────────────────────────────────────────────

    /** @return кадры или null, если файл не читается */
    public List<MacroFrame> load(String filename) {
        Path file = resolve(filename);
        if (file == null) return null;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SavedMacro m = GSON.fromJson(r, SavedMacro.class);
            if (m != null && m.frames != null && !m.frames.isEmpty()) return m.frames;
            LOGGER.warn("В файле {} нет кадров", filename);
        } catch (Exception e) {
            LOGGER.error("Не удалось загрузить макрос {}", filename, e);
        }
        return null;
    }

    public boolean delete(String filename) {
        Path file = resolve(filename);
        if (file == null) return false;
        try {
            infoCache.remove(filename);
            return Files.deleteIfExists(file);
        } catch (Exception e) {
            LOGGER.error("Не удалось удалить {}", filename, e);
            return false;
        }
    }

    /** Защита от "../" в имени файла. */
    private static Path resolve(String filename) {
        Path p = MACRO_DIR.resolve(filename).normalize();
        return p.startsWith(MACRO_DIR) ? p : null;
    }

    public static String sanitize(String name) {
        return name.trim()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("^\\.+", "");
    }
}

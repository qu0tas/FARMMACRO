package com.farmmacro.macro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MacroStorage — сохраняет и загружает именованные макросы.
 *
 * Каждый макрос = отдельный файл .json в папке config/farmmacro_macros/.
 * Формат: {"name":"...", "frames":[{x,y,z,yaw,pitch,...}, ...]}
 */
public class MacroStorage {

    public static final MacroStorage INSTANCE = new MacroStorage();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path MACRO_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("farmmacro_macros");

    // ── Структура файла ───────────────────────────────────────────────────────

    public static class SavedMacro {
        public String           name;
        public List<MacroFrame> frames;

        public SavedMacro(String name, List<MacroFrame> frames) {
            this.name   = name;
            this.frames = frames;
        }
    }

    // ── Сохранение ────────────────────────────────────────────────────────────

    /**
     * Сохраняет макрос под заданным именем.
     * Имя sanitize-ится (убираем слеши и спецсимволы).
     * @return true если успешно
     */
    public boolean save(String name, List<MacroFrame> frames) {
        try {
            ensureDir();
            String safe = sanitize(name);
            if (safe.isEmpty()) return false;
            Path file = MACRO_DIR.resolve(safe + ".json");
            SavedMacro macro = new SavedMacro(name, new ArrayList<>(frames));
            try (Writer w = new FileWriter(file.toFile())) {
                GSON.toJson(macro, w);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Загрузка ──────────────────────────────────────────────────────────────

    /**
     * Загружает список всех сохранённых макросов (только имена + кол-во кадров).
     */
    public List<MacroInfo> listMacros() {
        List<MacroInfo> result = new ArrayList<>();
        try {
            ensureDir();
            File[] files = MACRO_DIR.toFile().listFiles(
                    (d, n) -> n.endsWith(".json"));
            if (files == null) return result;
            for (File f : files) {
                try (Reader r = new FileReader(f)) {
                    SavedMacro m = GSON.fromJson(r, SavedMacro.class);
                    if (m != null && m.name != null) {
                        result.add(new MacroInfo(
                                m.name,
                                m.frames == null ? 0 : m.frames.size(),
                                f.getName()
                        ));
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    /**
     * Загружает полный макрос по имени файла (filename из MacroInfo).
     * @return список кадров или null если ошибка
     */
    public List<MacroFrame> load(String filename) {
        try {
            ensureDir();
            Path file = MACRO_DIR.resolve(filename);
            try (Reader r = new FileReader(file.toFile())) {
                SavedMacro m = GSON.fromJson(r, SavedMacro.class);
                if (m != null && m.frames != null) return m.frames;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Удаляет макрос по имени файла.
     */
    public boolean delete(String filename) {
        try {
            ensureDir();
            return Files.deleteIfExists(MACRO_DIR.resolve(filename));
        } catch (Exception e) {
            return false;
        }
    }

    // ── Вспомогательный класс ─────────────────────────────────────────────────

    public static class MacroInfo {
        public final String name;
        public final int    frameCount;
        public final String filename; // имя файла для load/delete

        public MacroInfo(String name, int frameCount, String filename) {
            this.name       = name;
            this.frameCount = frameCount;
            this.filename   = filename;
        }

        @Override public String toString() { return name; }
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    private void ensureDir() throws IOException {
        if (!Files.exists(MACRO_DIR)) Files.createDirectories(MACRO_DIR);
    }

    private String sanitize(String name) {
        return name.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_");
    }
}

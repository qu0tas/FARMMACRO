package com.farmmacro.panic;

import com.farmmacro.macro.MacroFrame;
import com.farmmacro.macro.MacroStorage;
import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PanicMoveStorage — загружает макросы из config/farmmacro/panic_moves/.
 *
 * Формат файлов идентичен обычным макросам (SavedMacro JSON).
 * Просто скопируй .json файлы из farmmacro_macros/ в эту папку.
 */
public class PanicMoveStorage {

    public static final PanicMoveStorage INSTANCE = new PanicMoveStorage();

    private static final Path PANIC_MOVES_DIR =
            FabricLoader.getInstance().getConfigDir()
                    .resolve("farmmacro").resolve("panic_moves");

    private static final Gson GSON = new Gson();

    /** Имя файла последнего загруженного макроса (для исключения повторов). */
    private String lastLoadedFile = null;

    /** Возвращает список имён .json файлов в папке (для отображения в GUI). */
    public List<String> listFiles() {
        List<String> result = new ArrayList<>();
        try {
            ensureDir();
            File[] files = PANIC_MOVES_DIR.toFile().listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) return result;
            for (File f : files) result.add(f.getName());
            result.sort(String::compareToIgnoreCase);
        } catch (Exception ignored) {}
        return result;
    }

    /**
     * Загружает случайный макрос, исключая файл с именем excludeFile.
     * Обновляет lastLoadedFile.
     * @return список кадров или null если папка пуста / все файлы недоступны
     */
    public List<MacroFrame> loadRandom(String excludeFile) {
        try {
            ensureDir();
            File[] files = PANIC_MOVES_DIR.toFile().listFiles((d, n) -> n.endsWith(".json"));
            if (files == null || files.length == 0) return null;

            // Если файлов больше одного — исключаем последний использованный
            List<File> candidates = new ArrayList<>();
            for (File f : files) {
                if (files.length > 1 && f.getName().equals(excludeFile)) continue;
                candidates.add(f);
            }
            if (candidates.isEmpty()) return null;

            File chosen = candidates.get((int)(Math.random() * candidates.size()));
            List<MacroFrame> frames = loadFile(chosen);
            if (frames != null) {
                lastLoadedFile = chosen.getName();
            }
            return frames;
        } catch (Exception ignored) {}
        return null;
    }

    /** Имя файла последнего успешно загруженного макроса. */
    public String getLastLoadedFile() {
        return lastLoadedFile;
    }

    /** Путь к папке panic_moves (для кнопки "открыть папку" в GUI). */
    public Path getDir() {
        return PANIC_MOVES_DIR;
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private List<MacroFrame> loadFile(File file) {
        try (Reader r = new FileReader(file)) {
            MacroStorage.SavedMacro m = GSON.fromJson(r, MacroStorage.SavedMacro.class);
            if (m != null && m.frames != null && !m.frames.isEmpty()) return m.frames;
        } catch (Exception ignored) {}
        return null;
    }

    private void ensureDir() throws IOException {
        if (!Files.exists(PANIC_MOVES_DIR)) Files.createDirectories(PANIC_MOVES_DIR);
    }
}

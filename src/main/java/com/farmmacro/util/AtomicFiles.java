package com.farmmacro.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Запись файла «целиком или никак»: сначала во временный {@code <имя>.tmp} рядом с целью,
 * затем атомарная замена. При сбое посередине старый файл остаётся целым, а .tmp не копится.
 * Единая точка для конфига, профилей, маршрутов и записей действий.
 */
public final class AtomicFiles {
    private AtomicFiles() {}

    @FunctionalInterface
    public interface Body {
        void write(Writer w) throws IOException;
    }

    /** Пишет UTF-8 текст через {@code body} и атомарно подменяет {@code target}. */
    public static void writeUtf8(Path target, Body body) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                body.write(w);
            }
            replace(tmp, target);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                /* не маскируем исходную ошибку */
            }
        }
    }

    /**
     * ATOMIC_MOVE, а если ФС/антивирус его не дают (Windows иногда отвечает AccessDenied) —
     * обычная замена: так же вёл себя прежний код, только теперь одинаково во всех местах.
     */
    public static void replace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

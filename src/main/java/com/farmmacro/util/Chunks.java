package com.farmmacro.util;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

/**
 * Проверка «чанк загружен» для живого мира.
 * <p>Не через {@code LevelReader.hasChunkAt/hasChunk}: они помечены {@code @Deprecated}, а у {@code ClientLevel}
 * {@code hasChunk(int,int)} всегда возвращает {@code true} (javap 26.1.2) — на клиенте такая проверка ничего не проверяет.
 * {@code ChunkSource.hasChunk} спрашивает кеш чанков без загрузки ({@code getChunk(x, z, FULL, false) != null}).
 */
public final class Chunks {
    /** Загружен ли чанк, содержащий блочную колонку (x, z). */
    public static boolean loadedAt(Level level, int blockX, int blockZ) {
        return level.getChunkSource()
                .hasChunk(SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
    }

    private Chunks() {}
}

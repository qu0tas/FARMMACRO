package com.farmmacro.route;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Map;

/**
 * v1.16.2: копия блоков вокруг игрока для поиска пути в фоне. Снимается в главном потоке ({@link #capture}):
 * секции чанков копируются ({@link LevelChunkSection#copy()}), дальше читаются из любого потока — мир клиента
 * поток поиска не трогает. Незагруженный чанк и всё вне снятой высоты — {@link Blocks#VOID_AIR}
 * (PathLevel считает это «опасно» — туда не идём). Блок-сущностей нет (getBlockEntity — null).
 */
public final class LevelSnapshot implements BlockGetter {
    private static final BlockState VOID = Blocks.VOID_AIR.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final int minY, height, secLo, secHi;
    private final Map<Long, LevelChunkSection[]> chunks = new HashMap<>();
    private final Map<Long, boolean[]> airOnly = new HashMap<>();
    /** Сколько секций скопировано (для журнала). */
    public int sections;

    private LevelSnapshot(int minY, int height, int secLo, int secHi) {
        this.minY = minY; this.height = height; this.secLo = secLo; this.secHi = secHi;
    }

    private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

    /** Только главный поток: снять блоки в прямоугольнике x0…x1, z0…z1 на высотах y0…y1 (включительно). */
    public static LevelSnapshot capture(Level level, int x0, int z0, int x1, int z1, int y0, int y1) {
        int lo = Math.max(level.getMinSectionY(), y0 >> 4), hi = Math.min(level.getMaxSectionY(), y1 >> 4);
        LevelSnapshot s = new LevelSnapshot(level.getMinY(), level.getHeight(), lo, hi);
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
            if (!level.hasChunk(cx, cz)) continue;
            LevelChunk ch = level.getChunk(cx, cz);
            LevelChunkSection[] src = ch.getSections();
            LevelChunkSection[] dst = new LevelChunkSection[Math.max(0, hi - lo + 1)];
            boolean[] air = new boolean[dst.length];
            for (int sy = lo; sy <= hi; sy++) {
                int idx = level.getSectionIndexFromSectionY(sy);
                if (idx < 0 || idx >= src.length || src[idx] == null || src[idx].hasOnlyAir()) { air[sy - lo] = true; continue; }
                dst[sy - lo] = src[idx].copy();
                s.sections++;
            }
            s.chunks.put(key(cx, cz), dst);
            s.airOnly.put(key(cx, cz), air);
        }
        return s;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        long k = key(x >> 4, z >> 4);
        LevelChunkSection[] secs = chunks.get(k);
        if (secs == null) return VOID;
        int si = (y >> 4) - secLo;
        if (si < 0 || si >= secs.length || (y >> 4) > secHi) return VOID;
        LevelChunkSection sec = secs[si];
        if (sec == null) return airOnly.get(k)[si] ? AIR : VOID;
        return sec.getBlockState(x & 15, y & 15, z & 15);
    }

    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    @Override public int getHeight() { return height; }
    @Override public int getMinY() { return minY; }
}

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
    public static final int MAX_CAPTURE_SECTIONS=4096;
    public static final class CaptureLimit extends RuntimeException {private CaptureLimit(){super("снимок пути превысил 4096 непустых секций; уменьши радиус");}}

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
                if(s.sections>=MAX_CAPTURE_SECTIONS)throw new CaptureLimit();
                dst[sy - lo] = src[idx].copy();
                s.sections++;
            }
            s.chunks.put(key(cx, cz), dst);
            s.airOnly.put(key(cx, cz), air);
        }
        return s;
    }

    public boolean containsChunk(long key) { return chunks.containsKey(key); }
    public java.util.Set<Long> chunkKeys() { return java.util.Set.copyOf(chunks.keySet()); }
    public static LevelSnapshot chunk(Level level, LevelChunk ch) {
        int lo=level.getMinSectionY(), hi=lo+ch.getSections().length-1;
        LevelSnapshot s=new LevelSnapshot(level.getMinY(),level.getHeight(),lo,hi);
        LevelChunkSection[] dst=new LevelChunkSection[ch.getSections().length];
        boolean[] air=new boolean[dst.length];
        for(int i=0;i<dst.length;i++) {
            var sec=ch.getSections()[i]; air[i]=sec==null || sec.hasOnlyAir();
            if(!air[i]) { dst[i]=sec.copy(); s.sections++; }
        }
        long k=key(ch.getPos().x(),ch.getPos().z()); s.chunks.put(k,dst);s.airOnly.put(k,air);
        return s;
    }
    /** Worker thread only. Retains exact top in sixteenth-block units and boundary plates. */
    public CachedChunk navigation(long k) {
        int cx=(int)(k>>32),cz=(int)k;
        PathLevel w=new PathLevel(this);
        java.util.Map<Integer,short[]> data=new java.util.TreeMap<>();
        for(int sy=secLo;sy<=secHi;sy++) {
            short[] cells=new short[4096];
            for(int y=0;y<16;y++) for(int z=0;z<16;z++) for(int x=0;x<16;x++)
                cells[(y<<8)|(z<<4)|x]=CachedChunk.cell(w,(cx<<4)+x,(sy<<4)+y,(cz<<4)+z);
            data.put(sy,cells);
        }
        return new CachedChunk(cx,cz,data);
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

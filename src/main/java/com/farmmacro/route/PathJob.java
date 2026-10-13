package com.farmmacro.route;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * v1.16.2: поиск пути в фоне. В главном потоке — только снимок блоков ({@link LevelSnapshot}, единицы мс);
 * A* ({@link PathFinder}) идёт в отдельном потоке «Kaelix-path», игра не подвисает. Результат забирают из тика
 * ({@link #done()} → {@link #result()}). Отменённый поиск досчитывается и выбрасывается.
 */
public final class PathJob {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Kaelix-path");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    /** Запас по высоте снимка над/под стартом и целями, блоков. */
    public static final int Y_MARGIN = 24;

    private final CompletableFuture<PathFinder.Result> fut;
    /** Время снимка в главном потоке, мкс; секций скопировано. */
    public final long snapMicros;
    public final int sections;

    private PathJob(CompletableFuture<PathFinder.Result> f, long snapMicros, int sections) {
        this.fut = f; this.snapMicros = snapMicros; this.sections = sections;
    }

    /** Только главный поток: снять блоки вокруг игрока и запустить поиск. */
    public static PathJob start(Minecraft mc, List<PathFinder.Goal> goals, boolean fly) {
        long t0 = System.nanoTime();
        LocalPlayer p = mc.player;
        PathFinder.Params prm = ReturnWalker.params(p, fly);
        double px = p.getX(), py = p.getY(), pz = p.getZ();
        boolean onGround = p.onGround();
        List<PathFinder.Goal> gl = List.copyOf(goals);
        int r = (int) Math.ceil(prm.maxDist) + 10;
        double lo = py, hi = py;
        for (PathFinder.Goal g : gl) { lo = Math.min(lo, g.y); hi = Math.max(hi, g.y); }
        int y0 = (int) Math.floor(lo) - Y_MARGIN, y1 = (int) Math.ceil(hi) + Y_MARGIN + (prm.fly ? prm.flyCeil + 4 : 0);
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
        LevelSnapshot snap = LevelSnapshot.capture(mc.level, bx - r, bz - r, bx + r, bz + r, y0, y1);
        long snapUs = (System.nanoTime() - t0) / 1000;
        ChunkCacheStore cache=ChunkCache.store(mc);
        ChunkCache.updateSnapshot(snap,cache);
        java.util.Set<Long> missing=new java.util.HashSet<>();
        if(cache!=null) for(int cx=(bx-r)>>4;cx<=(bx+r)>>4;cx++) for(int cz=(bz-r)>>4;cz<=(bz+r)>>4;cz++) {
            long k=CachedChunk.key(cx,cz); if(!snap.containsChunk(k)) missing.add(k);
        }
        CompletableFuture<PathFinder.Result> f = CompletableFuture.supplyAsync(() -> {
            CachedPathWorld world=new CachedPathWorld(new PathLevel(snap),snap::containsChunk,
                    cache==null ? java.util.Map.of() : cache.load(missing));
            PathFinder.Result result=new PathFinder(world,prm).find(px,py,pz,onGround,gl);
            result.cachedCells=world.witnesses(result,px,py,pz);
            return result;
        }, EXEC);
        return new PathJob(f, snapUs, snap.sections);
    }

    public boolean done() { return fut.isDone(); }

    /** Готовый результат (ошибка поиска — «нет пути» с причиной). */
    public PathFinder.Result result() {
        try {
            return fut.join();
        } catch (RuntimeException e) {
            PathFinder.Result r = new PathFinder.Result();
            Throwable c = e.getCause() != null ? e.getCause() : e;
            r.reason = "ошибка поиска пути: " + c;
            return r;
        }
    }

    public void cancel() { fut.cancel(false); }
}

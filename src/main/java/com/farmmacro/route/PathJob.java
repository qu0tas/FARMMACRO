package com.farmmacro.route;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;


/**
 * v1.16.2: поиск пути в фоне. В главном потоке — только снимок блоков ({@link LevelSnapshot}, единицы мс);
 * A* ({@link PathFinder}) идёт в отдельном потоке «Kaelix-path», игра не подвисает. Результат забирают из тика
 * ({@link #done()} → {@link #result()}). Отменённый поиск кооперативно прекращается на ближайшей итерации A*.
 */
public final class PathJob {
    private static final ThreadPoolExecutor EXEC=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"Kaelix-path");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-1);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static volatile String lastResult="NONE";
    public static String technicalState(){return "active="+EXEC.getActiveCount()+"; queued="+EXEC.getQueue().size()+"/1; last="+lastResult+"; worker budget=5s, existing node limit";}
    private volatile Runnable queuedTask;
    private static PathJob rejected(){lastResult="QUEUE_FULL";com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.PATH_REJECTED);var r=new PathFinder.Result();r.reason="очередь поиска занята (1 работа + 1 ожидание)";return new PathJob(CompletableFuture.completedFuture(r),0,0,new java.util.concurrent.atomic.AtomicBoolean());}
    /** Запас по высоте снимка над/под стартом и целями, блоков. */
    public static final int Y_MARGIN = 24;

    private final CompletableFuture<PathFinder.Result> fut;
    private final java.util.concurrent.atomic.AtomicBoolean cancelled;
    /** Время снимка в главном потоке, мкс; секций скопировано. */
    public final long snapMicros;
    public final int sections;

    private PathJob(CompletableFuture<PathFinder.Result> f, long snapMicros, int sections, java.util.concurrent.atomic.AtomicBoolean cancelled) {
        this.cancelled=cancelled;
        this.fut = f; this.snapMicros = snapMicros; this.sections = sections;
    }

    /** Только главный поток: снять блоки вокруг игрока и запустить поиск. */
    public static PathJob start(Minecraft mc, List<PathFinder.Goal> goals, boolean fly) {return start(mc,goals,fly,java.util.Map.of());}
    public static PathJob start(Minecraft mc,List<PathFinder.Goal> goals,boolean fly,java.util.Map<Long,Integer> recovery){
        if(EXEC.getQueue().remainingCapacity()==0)return rejected();
        long t0 = System.nanoTime();
        LocalPlayer p = mc.player;
        PathFinder.Params prm = ReturnWalker.params(p, fly);
        prm.recovery=java.util.Map.copyOf(recovery);
        var cancelled=new java.util.concurrent.atomic.AtomicBoolean();prm.cancelled=cancelled::get;
        double px = p.getX(), py = p.getY(), pz = p.getZ();
        boolean onGround = p.onGround();
        List<PathFinder.Goal> gl = List.copyOf(goals);
        int r = (int) Math.ceil(prm.maxDist) + 10;
        double lo = py, hi = py;
        for (PathFinder.Goal g : gl) { lo = Math.min(lo, g.y); hi = Math.max(hi, g.y); }
        int y0 = (int) Math.floor(lo) - Y_MARGIN, y1 = (int) Math.ceil(hi) + Y_MARGIN + (prm.fly ? prm.flyCeil + 4 : 0);
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz);
        LevelSnapshot snap;
        try{snap=LevelSnapshot.capture(mc.level,bx-r,bz-r,bx+r,bz+r,y0,y1);}
        catch(LevelSnapshot.CaptureLimit limit){lastResult="SNAPSHOT_LIMIT";com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.PATH_SNAPSHOT_LIMIT);var result=new PathFinder.Result();result.reason=limit.getMessage();return new PathJob(CompletableFuture.completedFuture(result),(System.nanoTime()-t0)/1000,LevelSnapshot.MAX_CAPTURE_SECTIONS,cancelled);}
        long snapUs = (System.nanoTime() - t0) / 1000;
        ChunkCacheStore cache=ChunkCache.store(mc);
        ChunkCache.updateSnapshot(snap,cache);
        java.util.Set<Long> missing=new java.util.HashSet<>();
        if(cache!=null) for(int cx=(bx-r)>>4;cx<=(bx+r)>>4;cx++) for(int cz=(bz-r)>>4;cz<=(bz+r)>>4;cz++) {
            long k=CachedChunk.key(cx,cz); if(!snap.containsChunk(k)) missing.add(k);
        }
        CompletableFuture<PathFinder.Result> f = new CompletableFuture<>();
        PathJob job=new PathJob(f,snapUs,snap.sections,cancelled);
        job.queuedTask=()->{
            try{
                if(cancelled.get()){lastResult="CANCELLED";return;}
                long deadline=System.nanoTime()+5_000_000_000L;
                prm.cancelled=()->cancelled.get()||System.nanoTime()-deadline>=0;
                CachedPathWorld world=new CachedPathWorld(new PathLevel(snap,true),snap::containsChunk,cache==null?java.util.Map.of():cache.load(missing,prm.cancelled));
                PathFinder.Result result=new PathFinder(world,prm).find(px,py,pz,onGround,gl);
                if(cancelled.get())lastResult="CANCELLED";
                else if(System.nanoTime()-deadline>=0){result.ok=false;result.reason="лимит времени поиска 5с";lastResult="TIME_BUDGET";}
                else lastResult=result.ok?"OK":"NO_PATH";
                if(!cancelled.get()&&result.ok)result.cachedCells=world.witnesses(result,px,py,pz);
                f.complete(result);
            }catch(Throwable t){lastResult="FAILED";f.completeExceptionally(t);}
            finally{job.queuedTask=null;com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.PATH_FINISHED);}
        };
        try{EXEC.execute(job.queuedTask);com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.PATH_SUBMITTED);}
        catch(RejectedExecutionException full){return rejected();}
        return job;
    }

    public boolean done() { return fut.isDone(); }

    /** Готовый результат (ошибка поиска — «нет пути» с причиной). */
    public PathFinder.Result result() {
        try {
            return fut.join();
        } catch (RuntimeException e) {
            PathFinder.Result r = new PathFinder.Result();
            Throwable c = e.getCause() != null ? e.getCause() : e;
            r.reason = "ошибка поиска пути: " + c.getClass().getSimpleName();
            return r;
        }
    }

    public void cancel() {if(cancelled.compareAndSet(false,true)){com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.PATH_CANCELLED);fut.cancel(false);var task=queuedTask;if(task!=null&&EXEC.remove(task))queuedTask=null;}}
}

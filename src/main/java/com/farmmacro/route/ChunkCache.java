package com.farmmacro.route;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.Path;

/** Main thread only copies sections; classification, compression and disk IO never touch the live world. */
public final class ChunkCache {
    private ChunkCache() {}
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r -> {
        Thread t=new Thread(r,"Kaelix-chunk-cache");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-1);return t;
    },new ThreadPoolExecutor.AbortPolicy());
    private static final AtomicLong epoch=new AtomicLong();
    private static final Set<String> pending=ConcurrentHashMap.newKeySet();
    private static volatile long lastWarn;
    private static final java.util.concurrent.atomic.AtomicBoolean clearRequested=new java.util.concurrent.atomic.AtomicBoolean();
    private record Write(LevelSnapshot snapshot,long key,ChunkCacheStore store,long epoch) {}
    private static final Map<String,Write> latest=new ConcurrentHashMap<>();
    private static void maintenance() throws java.io.IOException {
        if(clearRequested.getAndSet(false)) ChunkCacheStore.clear(root());
    }
    public static void updateSnapshot(LevelSnapshot snap,ChunkCacheStore store) {
        if(store==null) return;
        long e=epoch.get();
        try { IO.execute(() -> {
            try {
                maintenance();
                for(long k:snap.chunkKeys()) { if(epoch.get()!=e) break; store.put(snap.navigation(k)); }
            } catch(Exception ex) { warn(ex); }
        }); } catch(RejectedExecutionException full) { warn(full); }
    }
    public static String technicalState(){return "active="+IO.getActiveCount()+"; queued="+IO.getQueue().size()+"/32; pending="+pending.size()+"; coalesced="+latest.size();}
    public static Path root() { return ModConfig.modDir().resolve("cache"); }
    public static ChunkCacheStore store(Minecraft mc) {
        if(!ModConfig.INSTANCE.chunkCacheEnabled || mc.level==null || RouteBuffer.worldId(mc)==null) return null;
        return new ChunkCacheStore(root(),RouteBuffer.worldId(mc),RouteBuffer.dimensionId(mc),ModConfig.INSTANCE.chunkCacheMb*1024L*1024L);
    }
    public static void register() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            IO.shutdown();
            try { IO.awaitTermination(3,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
        },"Kaelix-cache-shutdown"));
        ClientChunkEvents.CHUNK_LOAD.register((level,ch) -> capture(level,ch));
        ClientChunkEvents.CHUNK_UNLOAD.register((level,ch) -> capture(level,ch));
    }
    public static void capture(Level level,LevelChunk ch) {
        Minecraft mc=Minecraft.getInstance();
        if(!mc.isSameThread() || mc.level!=level) return;
        ChunkCacheStore store=store(mc); if(store==null) return;
        long k=CachedChunk.key(ch.getPos().x(),ch.getPos().z()), e=epoch.get();
        String id=store.directory()+":"+k;
        if(IO.getQueue().remainingCapacity()==0&&!pending.contains(id)){com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.CHUNK_SKIPPED);return;}
        LevelSnapshot snap=LevelSnapshot.chunk(level,ch);
        synchronized(pending) {
            if(!pending.add(id)) { latest.put(id,new Write(snap,k,store,e)); return; }
        }
        try { IO.execute(() -> {
            try {
                maintenance();
                Write w=new Write(snap,k,store,e);
                while(w!=null) {
                    if(epoch.get()==w.epoch) { var nav=w.snapshot.navigation(w.key); if(epoch.get()==w.epoch) w.store.put(nav); }
                    synchronized(pending) {
                        w=latest.remove(id);
                        if(w==null) pending.remove(id);
                    }
                }
            } catch(Exception ex) {
                synchronized(pending) { pending.remove(id);latest.remove(id); }
                warn(ex);
            }
        }); } catch(RejectedExecutionException full) { synchronized(pending){pending.remove(id);latest.remove(id);}com.farmmacro.util.Diagnostics.add(com.farmmacro.util.Diagnostics.Counter.CHUNK_SKIPPED);warn(full); }
    }
    private static void warn(Exception e) {
        long now=System.currentTimeMillis();
        if(now-lastWarn>30000) { lastWarn=now;com.farmmacro.FarmMacroMod.LOGGER.warn("Кэш чанков: {}",e.toString()); }
    }
    /** Explicit clear is serialized after old writes; epochs cancel queued snapshots, future snapshots may refill. */
    public static void clear() {
        epoch.incrementAndGet(); clearRequested.set(true);
        try { IO.execute(() -> { try { maintenance(); } catch(Exception e){warn(e);} }); }
        catch(RejectedExecutionException full) { warn(full); }
    }
    private static int tick;
    /** Periodically refresh loaded chunks even if they were already loaded when caching was enabled. */
    public static void tick(Minecraft mc) {
        if(mc.player==null || mc.level==null || !ModConfig.INSTANCE.chunkCacheEnabled) return;
        if(++tick%5!=0) return;
        int r=Math.min(16,mc.options.renderDistance().get()), side=2*r+1, index=(tick/5)%(side*side);
        int cx=(int)Math.floor(mc.player.getX())>>4,cz=(int)Math.floor(mc.player.getZ())>>4;
        cx+=index%side-r;cz+=index/side-r;
        if(mc.level.hasChunk(cx,cz)) capture(mc.level,mc.level.getChunk(cx,cz));
    }
}
